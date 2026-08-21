package com.gto.recipesearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngredientTableTest {

    private static IngredientTable table(int[] ids, long[] amounts) {
        IngredientTable table = new IngredientTable(ids);
        table.amounts = amounts;
        return table;
    }

    @Test
    void matchWhenInventoryHasEnoughOfEach() {
        IngredientTable table = table(new int[]{5, 7}, new long[]{2, 4});
        IntLongMap inventory = new IntLongMap();
        inventory.add(5, 2);
        inventory.add(7, 4);
        assertTrue(table.match(inventory));
    }

    @Test
    void matchFailsWhenAmountIsShort() {
        IngredientTable table = table(new int[]{5, 7}, new long[]{2, 4});
        IntLongMap inventory = new IntLongMap();
        inventory.add(5, 2);
        inventory.add(7, 3); // 差 1
        assertFalse(table.match(inventory));
    }

    @Test
    void matchFailsWhenIngredientMissing() {
        IngredientTable table = table(new int[]{5, 7}, new long[]{2, 4});
        IntLongMap inventory = new IntLongMap();
        inventory.add(5, 100);
        assertFalse(table.match(inventory));
    }

    @Test
    void matchSucceedsWithSurplus() {
        IngredientTable table = table(new int[]{5}, new long[]{2});
        IntLongMap inventory = new IntLongMap();
        inventory.add(5, 99);
        assertTrue(table.match(inventory));
    }

    @Test
    void matchOnEmptyTableAlwaysSucceeds() {
        IngredientTable table = table(new int[0], new long[0]);
        assertTrue(table.match(new IntLongMap()));
    }
}
