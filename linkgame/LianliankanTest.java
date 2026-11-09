package linkgame;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 连连看核心逻辑的无界面自检。
 *
 * <p>只依赖 JDK 自带能力，不启动 Swing 窗口，便于在 CI 或无显示环境中验证：
 * 连通判定（0/1/2 个拐点、绕棋盘外侧）、消除与计分、选中/取消、死局检测、
 * 自动重排以及胜负判断。</p>
 *
 * <p>运行方式（在仓库根目录）：</p>
 * <pre>
 * javac linkgame/Lianliankan.java linkgame/LianliankanTest.java
 * java linkgame.LianliankanTest
 * </pre>
 */
public final class LianliankanTest {

    private static int checks;

    private LianliankanTest() {
    }

    public static void main(String[] args) {
        testNewGameCountsAndSolvability();
        testSelectDeselectAndEmptyClick();
        testStraightPathAndScore();
        testOneTurnPath();
        testTwoTurnPath();
        testOutsideBorderRoute();
        testBlockedPairHasNoPath();
        testMismatchAndNoPath();
        testWinState();
        testTwoPairsUntilWin();
        testDeadlockDetectionAndReshuffle();
        testAutoReshuffleAfterMatch();
        testPathIntegrityOnRandomBoards();
        testPathCompletenessAgainstBfs();
        testRestartResetsEverything();

        System.out.println("全部 " + checks + " 项逻辑自检通过");
    }

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    /** 新棋盘：80 个图案、每种图案数量为偶数、至少存在一步可消。 */
    private static void testNewGameCountsAndSolvability() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(20260101L));

        checkEquals(Lianliankan.ROWS * Lianliankan.COLS, engine.remainingTiles(),
                "新棋盘应铺满图案");
        check(engine.hasAnyMove(), "新棋盘至少应有一对可消除的图案");
        checkEquals(0, engine.getScore(), "新棋盘得分应为 0");
        check(!engine.isWon() && !engine.isGameOver(), "新棋盘不应处于结束状态");

        int[] counts = new int[Lianliankan.KIND_COUNT + 1];
        for (int row = 0; row < Lianliankan.ROWS; row++) {
            for (int col = 0; col < Lianliankan.COLS; col++) {
                int kind = engine.valueAt(row, col);
                check(kind >= 1 && kind <= Lianliankan.KIND_COUNT,
                        "图案编号应在 1.." + Lianliankan.KIND_COUNT + " 之间，实际为 " + kind);
                counts[kind]++;
            }
        }
        for (int kind = 1; kind <= Lianliankan.KIND_COUNT; kind++) {
            check(counts[kind] > 0, "图案 " + kind + " 应至少出现一次");
            check(counts[kind] % 2 == 0, "图案 " + kind + " 的数量应为偶数，实际为 " + counts[kind]);
        }
    }

    /** 选中、再次点击取消；点击空格不会破坏已有选中状态。 */
    private static void testSelectDeselectAndEmptyClick() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(1L));
        engine.loadBoardForTest(board(
                cell(2, 2, 1), cell(2, 4, 1)));

        Lianliankan.GameEngine.ClickResult selected = engine.click(2, 2);
        checkEquals(Lianliankan.GameEngine.ClickType.SELECTED, selected.type, "首次点击应选中");
        check(engine.isSelected(2, 2), "选中状态应生效");

        Lianliankan.GameEngine.ClickResult empty = engine.click(6, 6);
        checkEquals(Lianliankan.GameEngine.ClickType.IGNORED, empty.type, "点击空格应被忽略");
        check(engine.isSelected(2, 2), "点击空格不应取消已有选中");

        Lianliankan.GameEngine.ClickResult deselected = engine.click(2, 2);
        checkEquals(Lianliankan.GameEngine.ClickType.DESELECTED, deselected.type,
                "再次点击同一格应取消选中");
        check(!engine.isSelected(2, 2), "取消后不应仍处于选中状态");
    }

    /** 直线连接：同一行、中间无阻挡；消除后加分并移除格子。 */
    private static void testStraightPathAndScore() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(2L));
        engine.loadBoardForTest(board(
                cell(2, 1, 1), cell(2, 4, 1),
                cell(5, 5, 2), cell(5, 6, 2)));

        List<Lianliankan.GameEngine.Cell> path = engine.findPath(2, 1, 2, 4);
        check(path != null, "同一行且中间为空时应可直线连接");
        checkEquals(0, turns(path), "直线路径的拐点数应为 0");
        checkEquals(2, path.size(), "直线路径应只包含起点和终点");

        Lianliankan.GameEngine.ClickResult result = engine.click(2, 1);
        checkEquals(Lianliankan.GameEngine.ClickType.SELECTED, result.type, "第一次点击应选中");
        result = engine.click(2, 4);
        checkEquals(Lianliankan.GameEngine.ClickType.MATCH, result.type, "相同图案应能匹配");
        check(result.path != null, "匹配结果应带连接路径");
        checkEquals(10, engine.getScore(), "每消除一对应加 10 分");
        checkEquals(1, engine.valueAt(2, 1), "动画结束前图案应仍保留在棋盘上");

        Lianliankan.GameEngine.Completion completion = engine.completeMatch();
        checkEquals(Lianliankan.GameEngine.Completion.NORMAL, completion, "剩余 2 个图案时应继续游戏");
        checkEquals(0, engine.valueAt(2, 1), "消除后第一个格子应变为空");
        checkEquals(0, engine.valueAt(2, 4), "消除后第二个格子应变为空");
        checkEquals(2, engine.remainingTiles(), "应只剩另一对图案");
        checkEquals(10, engine.getScore(), "得分不应重复增加");
    }

    /** 一个拐点的 L 形连接。 */
    private static void testOneTurnPath() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(3L));
        engine.loadBoardForTest(board(
                cell(1, 1, 1), cell(3, 5, 1),
                cell(6, 0, 2), cell(6, 1, 2)));

        List<Lianliankan.GameEngine.Cell> path = engine.findPath(1, 1, 3, 5);
        check(path != null, "L 形空角存在时应能用一个拐点连通");
        checkEquals(1, turns(path), "L 形路径应恰好有 1 个拐点");
        checkEquals(3, path.size(), "L 形路径应包含起点、拐点、终点");
    }

    /** 两个拐点：同一行中间被挡，可绕到棋盘上方。 */
    private static void testTwoTurnPath() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(4L));
        engine.loadBoardForTest(board(
                cell(2, 1, 1), cell(2, 4, 1),
                cell(2, 2, 2),
                cell(6, 6, 3), cell(6, 7, 3)));

        List<Lianliankan.GameEngine.Cell> path = engine.findPath(2, 1, 2, 4);
        check(path != null, "直线被挡时应允许两个拐点绕行");
        checkEquals(2, turns(path), "绕行路径应恰好有 2 个拐点");
        check(!path.isEmpty() && path.get(0).row == 3, "路径起点应是带边框坐标中的原格子");
        check(path.get(path.size() - 1).row == 3, "路径终点应是带边框坐标中的原格子");
    }

    /** 路径可以绕到棋盘外侧，但仍不能穿过未消除的格子。 */
    private static void testOutsideBorderRoute() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(5L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 2), cell(0, 3, 1),
                cell(4, 4, 3), cell(4, 5, 3)));

        List<Lianliankan.GameEngine.Cell> path = engine.findPath(0, 0, 0, 3);
        check(path != null, "顶行图案被挡时可从棋盘外侧绕行");
        checkEquals(2, turns(path), "绕到棋盘外侧应恰好需要 2 个拐点");

        boolean usesOutside = false;
        for (Lianliankan.GameEngine.Cell point : path) {
            if (point.row == 0) {
                usesOutside = true;
            }
        }
        check(usesOutside, "绕行路径应经过带边框坐标 row=0 的棋盘外侧");
    }

    /** 2×2 交替布局是经典死局：两对图案都被对方挡住。 */
    private static void testBlockedPairHasNoPath() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(6L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 2),
                cell(1, 0, 2), cell(1, 1, 1)));

        check(engine.findPath(0, 0, 1, 1) == null, "死局中第一对图案不应有可行路径");
        check(engine.findPath(0, 1, 1, 0) == null, "死局中第二对图案不应有可行路径");
        check(!engine.hasAnyMove(), "2×2 交替布局应被判定为无解");
    }

    /** 图案不同、或相同但无路径时的提示类型与状态清理。 */
    private static void testMismatchAndNoPath() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(7L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 2),
                cell(1, 0, 2), cell(1, 1, 1)));

        Lianliankan.GameEngine.ClickResult result = engine.click(0, 0);
        checkEquals(Lianliankan.GameEngine.ClickType.SELECTED, result.type, "第一次点击应选中");
        result = engine.click(0, 1);
        checkEquals(Lianliankan.GameEngine.ClickType.MISMATCH, result.type, "不同图案应提示不匹配");
        check(!engine.isSelected(0, 0), "不匹配后应取消选中");
        checkEquals(0, engine.getScore(), "不匹配不应加分");

        result = engine.click(1, 1);
        checkEquals(Lianliankan.GameEngine.ClickType.SELECTED, result.type, "再次点击应重新选中");
        result = engine.click(0, 0);
        checkEquals(Lianliankan.GameEngine.ClickType.NO_PATH, result.type,
                "相同图案但无路径时应提示不可连通");
        check(!engine.isSelected(1, 1), "不可连通后应取消选中");
        checkEquals(4, engine.remainingTiles(), "失败操作不应移除图案");
    }

    /** 棋盘清空即胜利；胜利后点击不再改变状态。 */
    private static void testWinState() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(8L));
        engine.loadBoardForTest(board(cell(4, 4, 1), cell(4, 5, 1)));

        Lianliankan.GameEngine.ClickResult first = engine.click(4, 4);
        checkEquals(Lianliankan.GameEngine.ClickType.SELECTED, first.type, "第一次点击应选中");
        Lianliankan.GameEngine.ClickResult second = engine.click(4, 5);
        checkEquals(Lianliankan.GameEngine.ClickType.MATCH, second.type, "最后一对应能匹配");
        checkEquals(10, engine.getScore(), "最后一对同样加 10 分");

        Lianliankan.GameEngine.Completion completion = engine.completeMatch();
        checkEquals(Lianliankan.GameEngine.Completion.WON, completion, "清空棋盘应返回胜利");
        check(engine.isWon(), "应进入胜利状态");
        checkEquals(0, engine.remainingTiles(), "胜利后棋盘应为空");

        Lianliankan.GameEngine.ClickResult afterWin = engine.click(0, 0);
        checkEquals(Lianliankan.GameEngine.ClickType.IGNORED, afterWin.type,
                "胜利后点击应被忽略");
        checkEquals(10, engine.getScore(), "胜利后点击不应改变得分");
    }

    /** 两对图案依次消除，得分累计 20 并通关。 */
    private static void testTwoPairsUntilWin() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(9L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 1),
                cell(2, 2, 2), cell(2, 3, 2)));

        engine.click(0, 0);
        checkEquals(Lianliankan.GameEngine.ClickType.MATCH, engine.click(0, 1).type,
                "第一对应能匹配");
        checkEquals(Lianliankan.GameEngine.Completion.NORMAL, engine.completeMatch(),
                "还剩一对时游戏应继续");

        engine.click(2, 2);
        checkEquals(Lianliankan.GameEngine.ClickType.MATCH, engine.click(2, 3).type,
                "第二对应能匹配");
        checkEquals(Lianliankan.GameEngine.Completion.WON, engine.completeMatch(),
                "第二对消除后应通关");
        checkEquals(20, engine.getScore(), "两对应累计 20 分");
        check(engine.isWon(), "应进入胜利状态");
    }

    /** 死局检测，以及自动重排后图案数量、种类保持不变且重新可解。 */
    private static void testDeadlockDetectionAndReshuffle() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(10L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 2),
                cell(1, 0, 2), cell(1, 1, 1)));

        check(!engine.hasAnyMove(), "重排前应判定为死局");
        Map<Integer, Integer> before = countKinds(engine);
        int beforeTiles = engine.remainingTiles();

        boolean reshuffled = engine.reshuffleRemaining();
        check(reshuffled, "自动重排应成功构造出至少一步可走的棋盘");
        check(engine.hasAnyMove(), "重排后应至少存在一对可消除的图案");
        checkEquals(beforeTiles, engine.remainingTiles(), "重排不应增减图案数量");
        checkEquals(before, countKinds(engine), "重排不应改变各图案的数量");
    }

    /** 消除最后一对后棋盘无解时，completeMatch 应自动重排而不是直接结束。 */
    private static void testAutoReshuffleAfterMatch() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(12L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 1),
                // 这个 2×2 交替块是死局，用来制造“消除后无解”的局面。
                cell(2, 2, 2), cell(2, 3, 3),
                cell(3, 2, 3), cell(3, 3, 2)));

        engine.click(0, 0);
        checkEquals(Lianliankan.GameEngine.ClickType.MATCH, engine.click(0, 1).type,
                "先消除一对普通图案");
        checkEquals(Lianliankan.GameEngine.Completion.RESHUFFLED, engine.completeMatch(),
                "消除后若剩余图案无解，应自动重排");
        check(engine.hasAnyMove(), "自动重排后应至少存在一步可走");
        checkEquals(4, engine.remainingTiles(), "自动重排不应改变剩余图案数量");
        checkEquals(10, engine.getScore(), "自动重排不应影响已获得的分数");
    }

    /**
     * 随机棋盘上的路径完整性：每条路径拐点不超过 2，首尾正确，
     * 相邻点同行/同列，且中间经过的格子都为空。
     */
    private static void testPathIntegrityOnRandomBoards() {
        for (long seed = 1; seed <= 5; seed++) {
            Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(seed));
            int verified = 0;
            for (int row1 = 0; row1 < Lianliankan.ROWS && verified < 20; row1++) {
                for (int col1 = 0; col1 < Lianliankan.COLS && verified < 20; col1++) {
                    if (engine.valueAt(row1, col1) == 0) {
                        continue;
                    }
                    for (int row2 = 0; row2 < Lianliankan.ROWS && verified < 20; row2++) {
                        for (int col2 = 0; col2 < Lianliankan.COLS && verified < 20; col2++) {
                            if (engine.valueAt(row2, col2) == 0
                                    || (row1 == row2 && col1 == col2)
                                    || engine.valueAt(row1, col1) != engine.valueAt(row2, col2)) {
                                continue;
                            }
                            List<Lianliankan.GameEngine.Cell> path =
                                    engine.findPath(row1, col1, row2, col2);
                            if (path == null) {
                                continue;
                            }
                            verified++;
                            check(path.size() >= 2, "路径至少应包含两个点");
                            check(turns(path) <= 2, "路径拐点数不能超过 2");
                            check(path.get(0).equals(new Lianliankan.GameEngine.Cell(row1 + 1, col1 + 1)),
                                    "路径起点坐标应为带边框坐标");
                            check(path.get(path.size() - 1).equals(
                                            new Lianliankan.GameEngine.Cell(row2 + 1, col2 + 1)),
                                    "路径终点坐标应为带边框坐标");
                            for (int i = 1; i < path.size(); i++) {
                                Lianliankan.GameEngine.Cell a = path.get(i - 1);
                                Lianliankan.GameEngine.Cell b = path.get(i);
                                check(a.row == b.row || a.col == b.col,
                                        "路径每一段都必须是横线或竖线");
                            }
                            for (int i = 1; i < path.size() - 1; i++) {
                                check(isPaddedEmpty(engine, path.get(i)),
                                        "路径中间经过的格子必须为空：" + path.get(i));
                            }
                        }
                    }
                }
            }
            check(verified > 0, "随机棋盘中应能验证到至少一条路径");
        }
    }

    /**
     * 用独立的 BFS 参考实现校验 findPath 的完整性：
     * 对所有同图案格子对，两者判定“能否在不超过 2 个拐点内连通”必须一致。
     */
    private static void testPathCompletenessAgainstBfs() {
        int checked = 0;
        for (long seed = 1; seed <= 2; seed++) {
            Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(seed));
            for (int row1 = 0; row1 < Lianliankan.ROWS; row1++) {
                for (int col1 = 0; col1 < Lianliankan.COLS; col1++) {
                    if (engine.valueAt(row1, col1) == 0) {
                        continue;
                    }
                    for (int row2 = 0; row2 < Lianliankan.ROWS; row2++) {
                        for (int col2 = 0; col2 < Lianliankan.COLS; col2++) {
                            if (engine.valueAt(row2, col2) != engine.valueAt(row1, col1)
                                    || (row1 == row2 && col1 == col2)) {
                                continue;
                            }
                            boolean expected = referencePathExists(engine, row1, col1, row2, col2);
                            boolean actual = engine.findPath(row1, col1, row2, col2) != null;
                            checkEquals(expected, actual,
                                    "findPath 与 BFS 参考结果不一致：(" + row1 + "," + col1
                                            + ") -> (" + row2 + "," + col2 + ")");
                            checked++;
                        }
                    }
                }
            }
        }
        check(checked > 0, "应至少校验一对图案");
    }

    /** 重新开始会清空得分、选中状态并重新铺满棋盘。 */
    private static void testRestartResetsEverything() {
        Lianliankan.GameEngine engine = new Lianliankan.GameEngine(new Random(11L));
        engine.loadBoardForTest(board(
                cell(0, 0, 1), cell(0, 1, 1),
                cell(2, 2, 2), cell(2, 3, 2)));
        engine.click(0, 0);
        engine.click(0, 1);
        engine.completeMatch();
        checkEquals(10, engine.getScore(), "消除一对后得分应为 10");

        engine.newGame();
        checkEquals(0, engine.getScore(), "重新开始后得分应清零");
        checkEquals(Lianliankan.ROWS * Lianliankan.COLS, engine.remainingTiles(),
                "重新开始后应重新铺满棋盘");
        check(!engine.isWon() && !engine.isGameOver(), "重新开始后不应处于结束状态");
        check(engine.hasAnyMove(), "重新开始后应至少存在一步可走");
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static int[][] board(int[]... cells) {
        int[][] layout = new int[Lianliankan.ROWS][Lianliankan.COLS];
        for (int[] cell : cells) {
            layout[cell[0]][cell[1]] = cell[2];
        }
        return layout;
    }

    private static int[] cell(int row, int col, int kind) {
        return new int[]{row, col, kind};
    }

    /** 计算路径的方向变化次数，即拐点数。 */
    private static int turns(List<Lianliankan.GameEngine.Cell> path) {
        int turns = 0;
        int previousRowDelta = 0;
        int previousColDelta = 0;
        for (int i = 1; i < path.size(); i++) {
            int rowDelta = Integer.signum(path.get(i).row - path.get(i - 1).row);
            int colDelta = Integer.signum(path.get(i).col - path.get(i - 1).col);
            if (rowDelta == 0 && colDelta == 0) {
                continue;
            }
            if (previousRowDelta != 0 || previousColDelta != 0) {
                if (rowDelta != previousRowDelta || colDelta != previousColDelta) {
                    turns++;
                }
            }
            previousRowDelta = rowDelta;
            previousColDelta = colDelta;
        }
        return turns;
    }

    /** 按带边框坐标判断格子是否为空。 */
    private static boolean isPaddedEmpty(Lianliankan.GameEngine engine,
                                         Lianliankan.GameEngine.Cell cell) {
        if (cell.row == 0 || cell.row == Lianliankan.ROWS + 1
                || cell.col == 0 || cell.col == Lianliankan.COLS + 1) {
            return true;
        }
        if (cell.row < 1 || cell.row > Lianliankan.ROWS
                || cell.col < 1 || cell.col > Lianliankan.COLS) {
            return false;
        }
        return engine.valueAt(cell.row - 1, cell.col - 1) == 0;
    }

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    /**
     * 独立参考实现：在带边框棋盘上按方向做 BFS，状态为（行、列、方向、已用拐点数），
     * 只要存在一条拐点不超过 2 的路径就返回 true。
     */
    private static boolean referencePathExists(Lianliankan.GameEngine engine,
                                               int row1, int col1, int row2, int col2) {
        int rows = Lianliankan.ROWS + 2;
        int cols = Lianliankan.COLS + 2;
        boolean[][][][] seen = new boolean[rows][cols][4][3];
        Deque<int[]> queue = new ArrayDeque<int[]>();
        int startRow = row1 + 1;
        int startCol = col1 + 1;
        int targetRow = row2 + 1;
        int targetCol = col2 + 1;

        for (int direction = 0; direction < 4; direction++) {
            seen[startRow][startCol][direction][0] = true;
            queue.add(new int[]{startRow, startCol, direction, 0});
        }

        while (!queue.isEmpty()) {
            int[] state = queue.poll();
            int row = state[0];
            int col = state[1];
            int direction = state[2];
            int turns = state[3];
            int nextRow = row + DR[direction];
            int nextCol = col + DC[direction];
            if (nextRow < 0 || nextRow >= rows || nextCol < 0 || nextCol >= cols) {
                continue;
            }
            if (nextRow == targetRow && nextCol == targetCol) {
                return true;
            }
            if (!isPaddedEmpty(engine,
                    new Lianliankan.GameEngine.Cell(nextRow, nextCol))) {
                continue;
            }
            for (int nextDirection = 0; nextDirection < 4; nextDirection++) {
                int nextTurns = turns + (nextDirection == direction ? 0 : 1);
                if (nextTurns > 2) {
                    continue;
                }
                if (!seen[nextRow][nextCol][nextDirection][nextTurns]) {
                    seen[nextRow][nextCol][nextDirection][nextTurns] = true;
                    queue.add(new int[]{nextRow, nextCol, nextDirection, nextTurns});
                }
            }
        }
        return false;
    }

    private static Map<Integer, Integer> countKinds(Lianliankan.GameEngine engine) {
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (int row = 0; row < Lianliankan.ROWS; row++) {
            for (int col = 0; col < Lianliankan.COLS; col++) {
                int kind = engine.valueAt(row, col);
                if (kind != 0) {
                    Integer old = counts.get(kind);
                    counts.put(kind, old == null ? 1 : old + 1);
                }
            }
        }
        return counts;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static void checkEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "（期望=" + expected + "，实际=" + actual + "）");
        }
        checks++;
    }
}
