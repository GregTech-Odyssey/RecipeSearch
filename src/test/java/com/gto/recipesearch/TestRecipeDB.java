package com.gto.recipesearch;

import java.util.function.Predicate;

/**
 * {@link AbstractRecipeDB} 的测试实现：配方以“原料 ID → 数量”映射表示，
 * 匹配谓词校验背包输入是否满足配方所需数量。
 */
class TestRecipeDB extends AbstractRecipeDB<TestRecipe> {

    static Predicate<TestRecipe> matches(IntLongMap available) {
        return recipe -> {
            IngredientTable table = recipe.ingredientTable;
            return table == null || table.match(available);
        };
    }

    @Override
    protected IntLongMap extractIngredientMap(TestRecipe recipe) {
        return recipe.ingredients;
    }

    @Override
    protected void setIngredientTable(TestRecipe recipe, IngredientTable table) {
        recipe.ingredientTable = table;
    }
}
