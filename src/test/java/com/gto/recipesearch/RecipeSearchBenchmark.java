package com.gto.recipesearch;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 配方搜索的 JMH 微基准（权威性能测量）。
 *
 * <p>与 JUnit 冒烟测试不同，JMH 在独立 fork 的 JVM 中运行，自动处理 JIT 预热、
 * 死代码消除（通过 {@link Blackhole} 消费结果）、并给出统计误差。运行方式：
 * {@code ./gradlew jmh}，参数可在 {@link BenchmarkRunner} 中调整。
 *
 * <p>建库（10 万随机配方）在 {@link #setup()} 中完成，不计入测量；测量的是单次
 * 搜索的开销。查询预生成 1000 个并按轮转取用，避免反复测量同一查询。
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class RecipeSearchBenchmark {

    private static final int RECIPE_COUNT = 100_000;
    private static final int INGREDIENT_TYPES = 10_000;
    private static final int MAX_INGREDIENTS_PER_RECIPE = 100;
    private static final int MAX_QUANTITY = 100;
    private static final int QUERY_COUNT = 1_000;
    private static final int QUERY_SIZE = 100;

    private TestRecipeDB db;
    private final List<IntLongMap> queries = new ArrayList<>(QUERY_COUNT);
    private final List<int[]> searchKeys = new ArrayList<>(QUERY_COUNT);
    private final List<Predicate<TestRecipe>> predicates = new ArrayList<>(QUERY_COUNT);
    private int nextQuery;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        List<TestRecipe> recipes = new ArrayList<>(RECIPE_COUNT);
        for (int i = 0; i < RECIPE_COUNT; i++) {
            IntLongMap ingredients = new IntLongMap();
            int ingredientCount = random.nextInt(MAX_INGREDIENTS_PER_RECIPE) + 1;
            for (int j = 0; j < ingredientCount; j++) {
                ingredients.add(random.nextInt(INGREDIENT_TYPES), random.nextInt(MAX_QUANTITY) + 1);
            }
            recipes.add(new TestRecipe(ingredients));
        }
        db = TestRecipeDB.build(new TestRecipeDB(), recipes);

        for (int i = 0; i < QUERY_COUNT; i++) {
            IntLongMap query = new IntLongMap();
            int querySize = random.nextInt(QUERY_SIZE) + 1;
            for (int j = 0; j < querySize; j++) {
                query.add(random.nextInt(INGREDIENT_TYPES), random.nextInt(MAX_QUANTITY) + 1);
            }
            queries.add(query);
            searchKeys.add(query.toIntArray());
            predicates.add(TestRecipeDB.matches(query));
        }
    }

    private int nextQueryIndex() {
        int i = nextQuery;
        if (++nextQuery == QUERY_COUNT) nextQuery = 0;
        return i;
    }

    /** 遍历查询命中的所有配方（完整搜索结果，含 fallback） */
    @Benchmark
    public void searchAllMatches(Blackhole blackhole) {
        int i = nextQueryIndex();
        RecipeSearcher<TestRecipe> searcher = db.search(queries.get(i), searchKeys.get(i), predicates.get(i));
        searcher.forEach(blackhole::consume);
    }

    /** 只取第一个命中（findAny 语义，含 fallback 链） */
    @Benchmark
    public void findFirstMatch(Blackhole blackhole) {
        int i = nextQueryIndex();
        blackhole.consume(db.findAnyMatch(queries.get(i), searchKeys.get(i), predicates.get(i)));
    }
}
