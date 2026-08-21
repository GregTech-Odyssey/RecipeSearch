package com.gto.recipesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSearchTest {

    // ---------- helpers ----------

    /** 交替的 id, amount 对构造配方 */
    private static TestRecipe recipe(int... pairs) {
        IntLongMap map = new IntLongMap();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return new TestRecipe(map);
    }

    /** 交替的 id, amount 对构造背包 */
    private static IntLongMap inventory(int... pairs) {
        IntLongMap map = new IntLongMap();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static TestRecipeDB build(List<TestRecipe> recipes) {
        return TestRecipeDB.build(new TestRecipeDB(), recipes);
    }

    private static Set<TestRecipe> searchAll(TestRecipeDB db, IntLongMap inventory) {
        Set<TestRecipe> found = new HashSet<>();
        db.search(inventory, inventory.toIntArray(), TestRecipeDB.matches(inventory)).forEach(found::add);
        return found;
    }

    // ---------- basic matching ----------

    @Test
    void exactMatchIsFound() {
        TestRecipe target = recipe(11, 4, 22, 2);
        TestRecipeDB db = build(List.of(target));
        assertTrue(searchAll(db, inventory(11, 4, 22, 2)).contains(target));
    }

    @Test
    void surplusInventoryStillMatches() {
        TestRecipe target = recipe(11, 4, 22, 2);
        TestRecipeDB db = build(List.of(target));
        assertTrue(searchAll(db, inventory(11, 10, 22, 10)).contains(target));
    }

    @Test
    void insufficientQuantityMisses() {
        TestRecipe target = recipe(11, 4, 22, 2);
        TestRecipeDB db = build(List.of(target));
        assertFalse(searchAll(db, inventory(11, 3, 22, 2)).contains(target));
    }

    @Test
    void missingIngredientMisses() {
        TestRecipe target = recipe(11, 4, 22, 2);
        TestRecipeDB db = build(List.of(target));
        assertFalse(searchAll(db, inventory(11, 100)).contains(target));
    }

    @Test
    void unrelatedInventoryMisses() {
        TestRecipe target = recipe(11, 4);
        TestRecipeDB db = build(List.of(target));
        assertTrue(searchAll(db, inventory(99, 1)).isEmpty());
    }

    // ---------- shared prefixes ----------

    @Test
    void recipesSharingPrefixAreAllFound() {
        TestRecipe a = recipe(11, 1);
        TestRecipe b = recipe(11, 1, 22, 1);
        TestRecipe c = recipe(11, 1, 22, 1, 33, 1);
        TestRecipeDB db = build(List.of(a, b, c));
        Set<TestRecipe> found = searchAll(db, inventory(11, 1, 22, 1, 33, 1));
        assertTrue(found.contains(a));
        assertTrue(found.contains(b));
        assertTrue(found.contains(c));
    }

    @Test
    void partialPrefixOnlyFindsSubset() {
        TestRecipe only11 = recipe(11, 1);
        TestRecipe needs22 = recipe(11, 1, 22, 1);
        TestRecipeDB db = build(List.of(only11, needs22));
        Set<TestRecipe> found = searchAll(db, inventory(11, 1));
        assertTrue(found.contains(only11));
        assertFalse(found.contains(needs22));
    }

    // ---------- findAny ----------

    @Test
    void findAnyMatchReturnsRecipeOrNull() {
        TestRecipe target = recipe(11, 4);
        TestRecipeDB db = build(List.of(target));
        IntLongMap hit = inventory(11, 4);
        assertSame(target, db.findAnyMatch(hit, hit.toIntArray(), TestRecipeDB.matches(hit)));
        IntLongMap miss = inventory(99, 1);
        assertNull(db.findAnyMatch(miss, miss.toIntArray(), TestRecipeDB.matches(miss)));
    }

    // ---------- unindexed (empty) recipes ----------

    @Test
    void emptyRecipeIsMatchedByAnyInventory() {
        TestRecipe empty = recipe();
        TestRecipeDB db = build(List.of(empty));
        assertTrue(searchAll(db, inventory()).contains(empty));
        assertTrue(searchAll(db, inventory(1, 1, 2, 2)).contains(empty));
    }

    @Test
    void emptyRecipeIsFoundViaFallback() {
        TestRecipe empty = recipe();
        TestRecipe normal = recipe(1, 1);
        TestRecipeDB db = build(List.of(empty, normal));
        Predicate<TestRecipe> predicate = TestRecipeDB.matches(inventory());
        List<TestRecipe> viaFallback = new ArrayList<>();
        db.searchFallback(predicate).forEach(viaFallback::add);
        assertTrue(viaFallback.contains(empty));
        assertFalse(viaFallback.contains(normal));
    }

    @Test
    void emptyRecipeLibrarySearchDoesNotCrash() {
        // 回归：全空配方库 + 空背包搜索不应 NPE / 越界；空配方应被命中
        TestRecipe empty = recipe();
        TestRecipeDB db = build(List.of(empty));
        Set<TestRecipe> found = searchAll(db, inventory());
        assertTrue(found.contains(empty));
    }

    // ---------- deep recipes (multi-word skip set) ----------

    @Test
    void deepRecipeBeyondSingleWordSkipIsFound() {
        int depth = 70; // > 64：触发 SearchFrame 多字位图与帧栈扩容
        IntLongMap ingredients = new IntLongMap();
        IntLongMap inventory = new IntLongMap();
        for (int id = 0; id < depth; id++) {
            ingredients.add(id, 1);
            inventory.add(id, 1);
        }
        TestRecipe target = new TestRecipe(ingredients);
        TestRecipeDB db = build(List.of(target));
        assertTrue(searchAll(db, inventory).contains(target));
    }

    @Test
    void deepRecipeMissesWhenOneIngredientMissing() {
        int depth = 70;
        IntLongMap ingredients = new IntLongMap();
        IntLongMap inventory = new IntLongMap();
        for (int id = 0; id < depth; id++) {
            ingredients.add(id, 1);
            if (id != 40) inventory.add(id, 1); // 缺 id=40
        }
        TestRecipe target = new TestRecipe(ingredients);
        TestRecipeDB db = build(List.of(target));
        assertTrue(searchAll(db, inventory).isEmpty());
    }

    // ---------- consumption styles ----------

    @Test
    void streamReturnsSameMatches() {
        TestRecipe a = recipe(1, 1);
        TestRecipe b = recipe(1, 1, 2, 1);
        TestRecipeDB db = build(List.of(a, b));
        IntLongMap inventory = inventory(1, 1, 2, 1);
        long count = db.search(inventory, inventory.toIntArray(), TestRecipeDB.matches(inventory)).stream().count();
        assertEquals(2, count);
    }

    @Test
    void iteratorProtocolYieldsSameMatches() {
        TestRecipe a = recipe(1, 1);
        TestRecipe b = recipe(1, 1, 2, 1);
        TestRecipeDB db = build(List.of(a, b));
        IntLongMap inventory = inventory(1, 1, 2, 1);
        List<TestRecipe> found = new ArrayList<>();
        for (TestRecipe recipe : db.search(inventory, inventory.toIntArray(), TestRecipeDB.matches(inventory))) {
            found.add(recipe);
        }
        assertEquals(2, found.size());
        assertTrue(found.contains(a));
        assertTrue(found.contains(b));
    }

    @Test
    void searcherIsSingleUse() {
        TestRecipeDB db = build(List.of(recipe(1, 1)));
        IntLongMap inventory = inventory(1, 1);
        RecipeSearcher<TestRecipe> searcher = db.search(inventory, inventory.toIntArray(), TestRecipeDB.matches(inventory));
        List<TestRecipe> first = new ArrayList<>();
        searcher.forEach(first::add);
        assertEquals(1, first.size());
        assertFalse(searcher.hasNext());
    }

    // ---------- build robustness ----------

    @Test
    void buildRejectsNullRecipesSilently() {
        TestRecipe a = recipe(1, 1);
        TestRecipeDB db = TestRecipeDB.build(new TestRecipeDB(), Arrays.asList(a, null, recipe(2, 2)));
        IntLongMap inventory = inventory(1, 1, 2, 2);
        assertEquals(2, searchAll(db, inventory).size());
    }
}
