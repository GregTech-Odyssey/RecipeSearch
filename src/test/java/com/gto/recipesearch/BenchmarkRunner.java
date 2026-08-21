package com.gto.recipesearch;

/**
 * JMH 基准启动器。默认参数跑 {@link RecipeSearchBenchmark} 的两个方法；
 * 可用 {@code ./gradlew jmh --args="..."} 透传 JMH 参数覆盖。
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            args = new String[]{
                    "-f", "2",                    // fork 次数（每 fork 独立 JVM 重跑预热）
                    "-wi", "5",                   // 预热迭代
                    "-i", "10",                   // 正式迭代
                    "-r", "1s",                   // 每轮时长
                    "-bm", "avgt", "-tu", "us",   // 平均耗时，微秒单位
                    "-foe", "true",               // 出错即失败
                    "-jvmArgs", "-Xmx4g",         // fork JVM 堆（建库峰值约 2.7 GB）
                    "-rff", "build/reports/jmh/benchmark-result.txt",
                    "RecipeSearchBenchmark"
            };
        }
        org.openjdk.jmh.Main.main(args);
    }
}
