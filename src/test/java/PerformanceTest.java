import com.gto.recipesearch.AbstractRecipeDB;
import com.gto.recipesearch.IntLongMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class PerformanceTest {

    private static final int RECIPE_COUNT = 100000;
    private static final int INGREDIENT_TYPES = 10000;
    private static final int MAX_INGREDIENTS_PER_RECIPE = 100;
    private static final int MAX_QUANTITY = 100;
    private static final int QUERY_SIZE = 100;
    private static final int TEST_ITERATIONS = 100000;

    private static final Random random = new Random(42);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Initializing test data...");
        long startTime = System.nanoTime();
        List<Recipe> recipes = new ArrayList<>(RECIPE_COUNT);
        generateRandomRecipes(recipes);
        SimpleRecipeDB db = SimpleRecipeDB.build(new SimpleRecipeDB(),recipes);
        long initTime = System.nanoTime() - startTime;
        System.out.printf("Initialization completed in %.2f ms%n", initTime / 1_000_000.0);
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        System.out.printf("Data Used Memory: %.2f MB%n", (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0));
        System.out.println("\nStarting performance tests...");
        testSearchPerformance(db, true);
        while (true) {
            Thread.sleep(10);
            testSearchPerformance(db, true);
        }
    }

    private static void generateRandomRecipes(List<Recipe> recipes) {
        for (int i = 0; i < RECIPE_COUNT; i++) {
            IntLongMap ingredients = new IntLongMap();
            int ingredientCount = random.nextInt(MAX_INGREDIENTS_PER_RECIPE) + 1;
            for (int j = 0; j < ingredientCount; j++) {
                int ingredientId = random.nextInt(INGREDIENT_TYPES);
                int quantity = random.nextInt(MAX_QUANTITY) + 1;
                ingredients.add(ingredientId, quantity);
            }
            recipes.add(new Recipe(ingredients));
        }
    }

    private static void testSearchPerformance(AbstractRecipeDB<Recipe> db, boolean printf) {
        long totalSearchTime = 0;
        AtomicLong totalFoundTime = new AtomicLong();
        AtomicInteger foundRecipes = new AtomicInteger();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            IntLongMap searchQuery = generateRandomSearchQuery();
            Predicate<Recipe> predicate = SimpleRecipeDB.matchPredicate(searchQuery);
            long startTime = System.nanoTime();
            AtomicBoolean first = new AtomicBoolean(true);
            db.search(searchQuery, searchQuery.toIntArray(), predicate).forEach(recipe -> {
                foundRecipes.getAndIncrement();
                if (first.get()) {
                    totalFoundTime.addAndGet(System.nanoTime() - startTime);
                    first.set(false);
                }
            });

            totalSearchTime += System.nanoTime() - startTime;
        }

        if (printf) {
            System.out.printf("\nSearch Performance:%n");
            System.out.printf("Average time per search: %.2f μs%n", (totalSearchTime / TEST_ITERATIONS) / 1_000.0);
            System.out.printf("Average time per found: %.2f μs%n", (totalFoundTime.get() / TEST_ITERATIONS) / 1_000.0);
            System.out.printf("Total recipes found: %d%n", foundRecipes.get());
            System.out.printf("Average recipes per search: %.2f%n", (double) foundRecipes.get() / TEST_ITERATIONS);

            Runtime runtime = Runtime.getRuntime();
            System.out.printf("Used Memory: %.2f MB%n", (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0));
        }
    }

    private static IntLongMap generateRandomSearchQuery() {
        int querySize = random.nextInt(QUERY_SIZE) + 1;
        IntLongMap query = new IntLongMap(querySize);
        for (int i = 0; i < querySize; i++) {
            int ingredientId = random.nextInt(INGREDIENT_TYPES);
            int quantity = random.nextInt(MAX_QUANTITY) + 1;
            query.add(ingredientId, quantity);
        }

        return query;
    }
}
