package com.gto.recipesearch;

/**
 * 测试用的最小配方模型：一张“原料 ID → 数量”映射，
 * 外加建库时由 {@link TestRecipeDB} 注入的原料表反向引用。
 */
class TestRecipe {

    final IntLongMap ingredients;
    IngredientTable ingredientTable;

    TestRecipe(IntLongMap ingredients) {
        this.ingredients = ingredients;
    }

    @Override
    public String toString() {
        return ingredients.toString();
    }
}
