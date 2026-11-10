package timsort

/**
 * テスト・効率レポート（比較回数・メモリ割り当て量）・ベンチマークの対象となる実装の一覧。
 * 改良版を作るたびに、新たに達成した効率の目標（Goal）と一緒に末尾へ追加していく。
 * レポートでは各実装を「1 つ前の実装」と比較して増減を表示する。
 *
 * 目標は累積する: ある段階で宣言した目標は、それ以降の全段階でも自動的に検証される。
 * これで「前の段階で達成した性能を後の段階で落とす」デグレを検出できる。
 *
 * 例:
 *   Step(MergeSort),
 *   Step(NaturalMergeSort, Goal.ASCENDING_LINEAR, Goal.DESCENDING_LINEAR),
 *   Step(MergeSortWithBinaryInsertion, Goal.NEAR_OPTIMAL_RANDOM),
 *   Step(MergeSortReusingTmp, Goal.TMP_REUSE),
 *   Step(TimSort, Goal.GALLOPING, Goal.TMP_HALF),
 *   Step(TunedTimSort, Goal.JDK_PARITY), // 仕上げ: 全パターンで JDK 並み
 */
val STEPS: List<Step> = listOf(
    // Step(StdlibSort), // 仮実装。最初の段階を追加したら外す
    Step(MergeSort),
    Step(MergeSortWithEarlyReturn, Goal.ASCENDING_LINEAR),
    Step(MergeSortWithInsertionSort),
    Step(MergeSortWithBinaryInsertionSort, Goal.NEAR_OPTIMAL_RANDOM),
    Step(NaturalMergeSort),
//    Step(NaturalMergeSortDetectingDescRun, Goal.DESCENDING_LINEAR),
    Step(NaturalMergeSortWithMinRun, Goal.DESCENDING_LINEAR),
    Step(NaturalMergeSortWithMinRunV2),
)

class Step(val sorter: Sorter, vararg val newGoals: Goal)

val SORTERS: List<Sorter> = STEPS.map { it.sorter }

/** 各実装が満たすべき目標（その段階までに宣言された目標の和集合）。 */
val GOALS_BY_SORTER: Map<Sorter, Set<Goal>> = buildMap {
    val acc = mutableSetOf<Goal>()
    for (step in STEPS) {
        acc += step.newGoals
        put(step.sorter, acc.toSet())
    }
}

/** レポートの基準列として常に表示する JDK の実装（SORTERS の内容に関係なく固定）。全目標を満たす。 */
object JdkReference : Sorter {
    override val name = "JDK(参考)"

    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        java.util.Arrays.sort(a, fromIndex, toIndex, c)
    }
}
