package com.gto.recipesearch;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.*;
import java.util.function.Predicate;

/**
 * Base class for a recipe database: builds the search index from a collection of recipes
 * and exposes the query entry points.
 *
 * <p>Building happens in three phases (see {@link #build}): ingredient ids are first
 * counted across all recipes, then each recipe's ingredients are reordered so the rarest
 * ones come first (rare probes prune the search earlier), and finally the trie is
 * constructed. Recipes with no ingredients are kept out of the trie and stored in the
 * {@code unindexedSerial}/{@code unindexedParallel} fallback lists, which are scanned
 * linearly (possibly in parallel) only when the trie search misses.
 */
@SuppressWarnings("unused")
public abstract class AbstractRecipeDB<R> {

    public static <R, DB extends AbstractRecipeDB<R>> DB build(DB database, Collection<R> recipes) {
        database.clear();
        var branchBuildTasks = new ArrayList<Runnable>(recipes.size());
        var ingredientTables = new Reference2ReferenceOpenHashMap<R, Pair<IntLongMap, IngredientTable>>(recipes.size());
        Int2IntOpenHashMap frequencyMap = new Int2IntOpenHashMap();
        branchBuildTasks.add(() -> database.rootBranch = database.rootBranch.optimize());
        recipes.forEach(recipe -> database.collectRecipeData(frequencyMap, ingredientTables, recipe));
        ingredientTables.forEach((recipe, pair) -> database.reorderByFrequency(frequencyMap, pair));
        ingredientTables.forEach((recipe, pair) -> database.addToBranch(branchBuildTasks, recipe, pair.right()));
        branchBuildTasks.forEach(Runnable::run);
        database.finishBuild();
        return database;
    }

    protected Branch<R> rootBranch;
    protected List<R> unindexedSerial;
    protected List<R> unindexedParallel;
    protected int maxSearchDepth;
    protected int minParallelThreshold = 1000;

    protected abstract IntLongMap extractIngredientMap(R recipe);

    protected abstract void setIngredientTable(R recipe, IngredientTable table);

    protected boolean supportsParallel(R recipe) {
        return true;
    }

    public R findAnyMatch(IntLongMap available, int[] searchKeys, Predicate<R> predicate) {
        R foundRecipe = new RecipeSearcher<>(maxSearchDepth, rootBranch, available, searchKeys, predicate, null).findAny();
        if (foundRecipe != null) return foundRecipe;
        if (!unindexedParallel.isEmpty()) {
            foundRecipe = findInParallel(unindexedParallel, predicate);
            if (foundRecipe != null) return foundRecipe;
        }
        if (!unindexedSerial.isEmpty()) {
            return findInSerial(unindexedSerial, predicate);
        }
        return null;
    }

    protected static <R> R findInParallel(List<R> recipes, Predicate<R> predicate) {
        return recipes.parallelStream().filter(predicate).findAny().orElse(null);
    }

    protected static <R> R findInSerial(List<R> recipes, Predicate<R> predicate) {
        for (R recipe : recipes) {
            if (predicate.test(recipe)) return recipe;
        }
        return null;
    }

    public Iterator<R> createFallbackIterator(Predicate<R> predicate) {
        Iterator<R> parallelIterator = unindexedParallel.isEmpty() ? null : unindexedParallel.parallelStream().filter(predicate).iterator();
        Iterator<R> serialIterator = unindexedSerial.isEmpty() ? null : IteratorUtil.filter(unindexedSerial.iterator(), predicate);
        if (parallelIterator == null) return serialIterator;
        if (serialIterator == null) return parallelIterator;
        return IteratorUtil.concat(parallelIterator, serialIterator);
    }

    public Iterable<R> searchFallback(Predicate<R> predicate) {
        Iterator<R> iterator = createFallbackIterator(predicate);
        return iterator == null ? Collections.emptyList() : IteratorUtil.asIterable(iterator);
    }

    public RecipeSearcher<R> search(IntLongMap available, int[] searchKeys, Predicate<R> predicate) {
        return new RecipeSearcher<>(maxSearchDepth, rootBranch, available, searchKeys, predicate, createFallbackIterator(predicate));
    }

    protected void finishBuild() {
        if (unindexedParallel.size() < minParallelThreshold) {
            unindexedSerial.addAll(unindexedParallel);
            unindexedParallel = Collections.emptyList();
        }
        if (unindexedSerial.isEmpty()) {
            unindexedSerial = Collections.emptyList();
        }
    }

    protected void addToBranch(List<Runnable> branchBuildTasks, R recipe, IngredientTable table) {
        int[] keys = table.ids;
        int searchDepth = keys.length;
        maxSearchDepth = Math.max(maxSearchDepth, searchDepth);
        addToBranch(branchBuildTasks, recipe, searchDepth, keys, rootBranch);
    }

    protected void addToBranch(List<Runnable> branchBuildTasks, R recipe, int depth, int[] keys, Branch<R> branch) {
        Branch<R> currentBranch = branch;
        int lastIndex = depth - 1;
        for (int i = 0; i < depth; i++) {
            boolean isIntermediateNode = i < lastIndex;
            Node<R> node = ((Branch.HashBranch<R>) currentBranch).compute(keys[i], (key, existingNode) -> isIntermediateNode ? Node.createBranch(branchBuildTasks, existingNode) : Node.createLeaf(branchBuildTasks, existingNode, recipe));
            if (isIntermediateNode) {
                currentBranch = ((Node.BranchNode<R>) node).branch();
            }
        }
    }

    protected void collectRecipeData(Int2IntOpenHashMap frequencyMap, Reference2ReferenceOpenHashMap<R, Pair<IntLongMap, IngredientTable>> ingredientTables, R recipe) {
        if (recipe == null) return;
        IntLongMap ingredientMap = extractIngredientMap(recipe);
        int[] keys = ingredientMap.toIntArray();
        if (keys.length == 0) {
            if (supportsParallel(recipe)) {
                unindexedParallel.add(recipe);
            } else {
                unindexedSerial.add(recipe);
            }
        } else {
            for (int key : keys) frequencyMap.addTo(key, 1);
            IngredientTable table = new IngredientTable(keys);
            setIngredientTable(recipe, table);
            ingredientTables.put(recipe, Pair.of(ingredientMap, table));
        }
    }

    protected void reorderByFrequency(Int2IntOpenHashMap frequencyMap, Pair<IntLongMap, IngredientTable> pair) {
        IntLongMap ingredientMap = pair.left();
        IngredientTable table = pair.right();
        int[] keys = table.ids;
        IntArrays.stableSort(keys, (a, b) -> Integer.compare(frequencyMap.get(a), frequencyMap.get(b)));
        int keyCount = keys.length;
        long[] amounts = new long[keyCount];
        for (int i = 0; i < keyCount; i++) {
            amounts[i] = ingredientMap.get(keys[i]);
        }
        table.amounts = amounts;
    }

    public void clear() {
        rootBranch = Branch.create();
        unindexedSerial = new ArrayList<>();
        unindexedParallel = new ArrayList<>();
        maxSearchDepth = 0;
    }
}
