package com.gto.recipesearch;

/**
 * Per-recipe ingredient table: the recipe's ingredient ids ordered by global rarity
 * (rarest first, see {@link AbstractRecipeDB#reorderRecipeByFrequency}) together with
 * the required quantity of each.
 *
 * <p>Serves both as the trie lookup key (the id array) and as the final quantity check
 * ({@link #match}) once a candidate recipe is reached.
 */
public class IngredientTable {

    final int[] ids;
    long[] amounts;

    public IngredientTable(int[] ids) {
        this.ids = ids;
    }

    /**
     * @return whether the given inventory map satisfies every required amount of this
     *         recipe
     */
    public boolean match(IntLongMap inventory) {
        final int[] ids = this.ids;
        final long[] amounts = this.amounts;
        for (int i = ids.length - 1; i >= 0; i--) {
            if (inventory.get(ids[i]) < amounts[i]) {
                return false;
            }
        }
        return true;
    }

}
