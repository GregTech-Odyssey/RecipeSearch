# RecipeSearch

为 Minecraft 模组（Forge）场景设计的高性能配方搜索库：给定玩家背包中"物品 ID → 数量"的映射，快速找出所有配方原料都被满足的配方。

## 特性

- **前缀树索引**：配方按原料 ID 建树，搜索是树上的深度优先遍历，无需扫描全部配方
- **稀有度优先剪枝**：建库时统计每个原料在所有配方中的出现频率，把最稀有的原料排在配方键的最前面，搜索时先用稀有原料探查，尽早剪掉不可能的分支
- **零分配迭代搜索**：搜索器用显式帧栈 + 跳位位图回溯，帧池化复用，热路径上不产生包装对象，适合每 tick 都要重算的 UI 场景
- **双模式探查**：每个搜索帧按"输入键数 vs 分支键数"自动选择按输入迭代或按分支迭代，总是走更短的一边
- **自动分支压缩**：建树完成后，少于 5 个键的小分支自动压成线性数组，避免小哈希表的开销
- **兜底策略**：没有原料的空配方不进树，按数量分流到串行 / 并行列表，仅在树搜索未命中时线性扫描

## 快速开始

### 依赖

```groovy
repositories {
    maven { url = 'https://maven.gtodyssey.com/releases' }
}

dependencies {
    implementation 'com.gto.recipesearch:RecipeSearch:26.8.3'
}
```

### 最小示例

继承 `AbstractRecipeDB`，实现两个抽象方法，让库能读取你的配方模型：

```java
public class MyRecipeDB extends AbstractRecipeDB<MyRecipe> {

    @Override
    protected IntLongMap extractIngredientMap(MyRecipe recipe) {
        return recipe.ingredients; // 配方的"原料 ID → 数量"映射
    }

    @Override
    protected void setIngredientTable(MyRecipe recipe, IngredientTable table) {
        recipe.ingredientTable = table; // 库把排序后的原料表注入配方，供数量校验使用
    }
}
```

建库并搜索：

```java
// 建库：传入全部配方
List<MyRecipe> recipes = ...;
MyRecipeDB db = MyRecipeDB.build(new MyRecipeDB(), recipes);

// 背包：当前拥有的物品
IntLongMap inventory = new IntLongMap();
inventory.add(11, 10);
inventory.add(22, 10);

// 匹配谓词：背包满足配方所需数量
Predicate<MyRecipe> predicate = r -> r.ingredientTable == null || r.ingredientTable.match(inventory);

// 遍历所有可合成配方
db.search(inventory, inventory.toIntArray(), predicate).forEach(System.out::println);

// 或只要第一个
MyRecipe any = db.findAnyMatch(inventory, inventory.toIntArray(), predicate);
```

## 工作原理

```
            ┌──────────────┐
 inventory  │  IntLongMap   │  背包物品 ID → 数量
            └──────┬───────┘
                   ▼
         ┌────────────────────┐
         │   RecipeSearcher    │  迭代式 DFS，帧栈 + 跳位位图
         └──────┬─────────────┘
                ▼
         ┌────────────────────┐
         │   Branch / Node     │  前缀树：按原料 ID 分层
         └────────────────────┘
                ▼
         ┌────────────────────┐
         │   IngredientTable   │  命中候选后做数量校验
         └────────────────────┘
```

- **建库**：统计原料频率 → 按稀有度重排每个配方的原料键 → 建 trie → 压缩小分支
- **搜索**：从根分支开始，逐层用背包里的原料探查树；每个帧记录已探索的输入下标（跳位位图），回溯时不会重复访问
- **校验**：树上候选只是"原料种类都出现过"，真正决定命中与否的是 `IngredientTable.match` 的数量比较

## 作为 Minecraft 模组安装

[Fast-Recipe-Search](https://github.com/nutant233/Fast-Recipe-Search)（包含当前库代码）。

## 构建与测试

```bash
./gradlew build        # 编译 + 单元测试 + 生成库 jar / sources jar
./gradlew test         # 仅运行单元测试
./gradlew jmh          # 运行 JMH 性能基准（权威测量）
```

性能用 **JMH**（JVM 微基准测试框架）测量：独立 fork 的 JVM 中运行，自动处理 JIT 预热、死代码消除与统计误差。基准用 10 万随机配方建库（建库不计入测量），测量单次搜索的平均耗时与置信区间。完整参数在 `BenchmarkRunner` 中可调，或用 `--args` 覆盖：

```bash
./gradlew jmh --args="-i 3 -wi 1 -f 1"   # 快速试跑（3 轮测量 / 1 轮预热 / 1 次 fork）
```

结果会输出到控制台并保存为 `build/reports/jmh/benchmark-result.txt`。

## 项目结构

```
src/main/java/com/gto/recipesearch/
├── AbstractRecipeDB.java   // 建库与查询入口（抽象基类）
├── Branch.java             // trie 分支：哈希表 / 小分支线性数组
├── Node.java               // 节点五种形状：叶子 / 叶子列表 / 纯分支 / 分支+配方
├── RecipeSearcher.java     // 迭代式搜索器（Iterator + Iterable + Stream）
├── SearchFrame.java        // 回溯帧：跳位位图（单字 / 多字）
├── IngredientTable.java    // 配方的原料表：稀有度排序的键 + 所需数量
├── IntLongMap.java         // 专用 int→long 哈希映射（累加 / 覆盖两种合并语义）
└── IteratorUtil.java       // 迭代器工具

src/test/java/com/gto/recipesearch/
├── RecipeSearchBenchmark.java  // JMH 性能基准
└── BenchmarkRunner.java        // JMH 启动器（可透传参数）
```
