package com.gto.recipesearch;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.*;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public abstract class AbstractRecipeDB<R> {

    public static <R, DB extends AbstractRecipeDB<R>> DB build(DB database, Collection<R> recipes) {
        database.clear();
        ArrayList<Runnable> branchConstructionTasks = new ArrayList<>(recipes.size());
        Map<R, Pair<IntLongMap, IntMapContainer>> recipeContainers = new Reference2ReferenceOpenHashMap<>(recipes.size());
        Int2IntOpenHashMap frequencyMap = new Int2IntOpenHashMap();
        branchConstructionTasks.add(() -> database.rootBranch = database.rootBranch.optimize());
        recipes.forEach(recipe -> database.collectRecipeData(frequencyMap, recipeContainers, recipe));
        recipeContainers.forEach((recipe, pair) -> database.reorderRecipeByFrequency(frequencyMap, pair));
        recipeContainers.forEach((recipe, pair) -> database.addToBranch(branchConstructionTasks, recipe, pair.right()));
        branchConstructionTasks.forEach(Runnable::run);
        database.finishBuild();
        return database;
    }

    protected Branch<R> rootBranch;
    protected List<R> serialRecipes;
    protected List<R> parallelRecipes;
    protected int maxSearchDepth;
    protected int minParallelThreshold = 1000;

    protected ThreadLocal<RecipeSearcher<R>> recipeSearcher = new ThreadLocal<>();

    protected abstract IntLongMap extractIntMap(R recipe);

    protected abstract void setRecipeContainer(R recipe, IntMapContainer container);

    protected boolean supportsParallel(R recipe) {
        return true;
    }

    protected RecipeSearcher<R> getRecipeSearcher() {
        var searcher = recipeSearcher.get();
        if (searcher == null) {
            searcher = new RecipeSearcher<>(this.maxSearchDepth);
            recipeSearcher.set(searcher);
        }
        return searcher;
    }

    public R findAnyMatch(IntLongMap map, int[] searchKeys, Predicate<R> predicate) {
        var searcher = getRecipeSearcher();
        searcher.reset(maxSearchDepth, rootBranch, map, searchKeys, predicate, null);
        R foundRecipe = searcher.findAny();
        if (foundRecipe != null) return foundRecipe;
        if (!parallelRecipes.isEmpty()) {
            foundRecipe = findInParallel(parallelRecipes, predicate);
            if (foundRecipe != null) return foundRecipe;
        }
        if (!serialRecipes.isEmpty()) {
            return findInSerial(serialRecipes, predicate);
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
        Iterator<R> parallelIterator = parallelRecipes.isEmpty() ? null : parallelRecipes.parallelStream().filter(predicate).iterator();
        Iterator<R> serialIterator = serialRecipes.isEmpty() ? null : IteratorUtil.filter(serialRecipes.iterator(), predicate);
        if (parallelIterator == null) return serialIterator;
        if (serialIterator == null) return parallelIterator;
        return IteratorUtil.concat(parallelIterator, serialIterator);
    }

    public Iterable<R> searchFallback(Predicate<R> predicate) {
        Iterator<R> iterator = createFallbackIterator(predicate);
        return iterator == null ? Collections.emptyList() : IteratorUtil.wrap(iterator);
    }

    public RecipeSearcher<R> search(IntLongMap map, int[] searchKeys, Predicate<R> predicate) {
        var searcher = getRecipeSearcher();
        searcher.reset(maxSearchDepth, rootBranch, map, searchKeys, predicate, createFallbackIterator(predicate));
        return searcher;
    }

    protected void finishBuild() {
        if (parallelRecipes.size() < minParallelThreshold) {
            serialRecipes.addAll(parallelRecipes);
            parallelRecipes = Collections.emptyList();
        }
        if (serialRecipes.isEmpty()) {
            serialRecipes = Collections.emptyList();
        }
    }

    protected void addToBranch(List<Runnable> branchConstructionTasks, R recipe, IntMapContainer container) {
        int[] keys = container.key;
        int searchDepth = keys.length;
        maxSearchDepth = Math.max(maxSearchDepth, searchDepth);
        addToBranch(branchConstructionTasks, recipe, searchDepth, keys, rootBranch);
    }

    protected void addToBranch(List<Runnable> branchConstructionTasks, R recipe, int depth, int[] keys, Branch<R> branch) {
        Branch<R> currentBranch = branch;
        int lastIndex = depth - 1;
        for (int i = 0; i < depth; i++) {
            boolean isIntermediateNode = i < lastIndex;
            Node<R> node = ((Branch.HashBranch<R>) currentBranch).compute(keys[i], (key, existingNode) -> isIntermediateNode ? Node.branch(branchConstructionTasks, existingNode) : Node.recipe(branchConstructionTasks, existingNode, recipe));
            if (isIntermediateNode) {
                currentBranch = ((Node.BranchNode<R>) node).branch();
            }
        }
    }

    protected void collectRecipeData(Int2IntOpenHashMap frequencyMap, Map<R, Pair<IntLongMap, IntMapContainer>> recipeContainers, R recipe) {
        if (recipe == null) return;
        IntLongMap intMap = extractIntMap(recipe);
        int[] keys = intMap.toIntArray();
        if (keys.length == 0) {
            if (supportsParallel(recipe)) {
                parallelRecipes.add(recipe);
            } else {
                serialRecipes.add(recipe);
            }
        } else {
            for (int key : keys) frequencyMap.addTo(key, 1);
            IntMapContainer container = new IntMapContainer(keys);
            setRecipeContainer(recipe, container);
            recipeContainers.put(recipe, Pair.of(intMap, container));
        }
    }

    protected void reorderRecipeByFrequency(Int2IntOpenHashMap frequencyMap, Pair<IntLongMap, IntMapContainer> pair) {
        IntLongMap intMap = pair.left();
        IntMapContainer container = pair.right();
        int[] keys = container.key;
        IntArrays.stableSort(keys, (a, b) -> Integer.compare(frequencyMap.get(a), frequencyMap.get(b)));
        int keyCount = keys.length;
        long[] values = new long[keyCount];
        for (int i = 0; i < keyCount; i++) {
            values[i] = intMap.get(keys[i]);
        }
        container.value = values;
    }

    public void clear() {
        rootBranch = Branch.create();
        serialRecipes = new ArrayList<>();
        parallelRecipes = new ArrayList<>();
        maxSearchDepth = 0;
    }
}
