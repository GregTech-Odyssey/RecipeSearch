import com.gto.recipesearch.AbstractRecipeDB;
import com.gto.recipesearch.IntLongMap;
import com.gto.recipesearch.IntMapContainer;
import com.gto.recipesearch.RecipeSearcher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SimpleRecipeDB extends AbstractRecipeDB<Recipe> {

    public static Predicate<Recipe> matchPredicate(IntLongMap map) {
        return recipe -> {
            IntMapContainer container = recipe.container;
            return container == null || container.match(map);
        };
    }

    @Override
    protected IntLongMap extractIntMap(Recipe recipe) {
        return recipe.input;
    }


    @Override
    protected void setRecipeContainer(Recipe recipe, IntMapContainer container) {
        recipe.container = container;
    }

    /**
     * 主方法，演示配方数据库的创建、填充和搜索功能
     */
    public static void main(String[] args) {
        List<Recipe> recipes = new ArrayList<>();

        // 创建输入映射，定义搜索条件
        IntLongMap input = new IntLongMap();
        // 添加多个键值对，每个键代表一种原料，值代表所需数量
        input.add(11, 10);
        input.add(22, 10);
        input.add(33, 10);
        input.add(44, 10);
        input.add(55, 10);
        input.add(66, 10);
        input.add(77, 10);
        input.add(88, 10);
        input.add(99, 10);
        input.add(12, 10);
        input.add(14, 10);
        input.add(16, 10);
        input.add(23, 10);
        input.add(43, 10);
        input.add(63, 10);
        input.add(83, 10);
        input.add(93, 10);
        input.add(111, 10);
        input.add(121, 10);
        input.add(131, 10);

        IntLongMap ri = new IntLongMap();
        ri.add(33, 4);  // 添加原料33，需要4个单位
        ri.add(66, 2);  // 添加原料66，需要2个单位
        ri.add(99, 2);  // 添加原料99，需要2个单位
        Recipe recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(22, 8);
        ri.add(55, 11);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(22, 8);
        ri.add(55, 5);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(11, 12);
        ri.add(44, 6);
        ri.add(77, 7);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(11, 4);
        ri.add(44, 6);
        ri.add(77, 7);
        ri.add(88, 7);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(11, 5);
        ri.add(22, 5);
        ri.add(88, 6);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(22, 5);
        ri.add(88, 6);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(11, 9);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        ri.add(22, 11);
        recipe = new Recipe(ri);
        recipes.add(recipe);

        ri = new IntLongMap();
        recipe = new Recipe(ri);
        recipes.add(recipe);

        SimpleRecipeDB db = SimpleRecipeDB.build(new SimpleRecipeDB(),recipes);

        // 创建搜索迭代器，查找匹配输入条件的配方
        int[] ints = input.toIntArray();
        Predicate<Recipe> predicate = matchPredicate(input);

        // 查找并打印第一个匹配的配方
        System.out.println(db.findAnyMatch(input, ints, predicate));
        // 遍历并打印所有匹配的配方
        db.search(input, ints, predicate).forEach(System.out::println);

        // 并行测试搜索性能
        IntList list = new IntArrayList(1000000);
        for (int i = 0; i < 1000000; i++) {
            list.add(i);
        }
        long start = System.currentTimeMillis();
        List<Recipe> rs = list.intStream().parallel()
                .mapToObj(i -> db.search(input, ints, predicate))
                .flatMap(RecipeSearcher::stream)
                .collect(Collectors.toList());
        System.out.println(rs.size());
        System.out.println(System.currentTimeMillis() - start);
    }
}
