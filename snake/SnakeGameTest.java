import java.awt.Point;

/**
 * 贪吃蛇核心规则的无界面自检，不启动窗口，可在纯命令行 / CI 中运行。
 *
 * <p>运行方式（在 snake/ 目录）：
 * <pre>
 *   javac SnakeGame.java SnakeGameTest.java
 *   java SnakeGameTest
 * </pre>
 */
public final class SnakeGameTest {

    private static int checks;

    private SnakeGameTest() {
    }

    public static void main(String[] args) {
        initialGameIsReady();
        movesOneCell();
        eatsFoodAndGrows();
        rejectsReverseDirection();
        wallCollisionEndsGame();
        selfCollisionEndsGame();
        movingIntoFreeingTailIsAllowed();
        foodStaysOffSnake();

        System.out.println("SnakeGameTest: " + checks + " 项检查全部通过");
    }

    private static void initialGameIsReady() {
        SnakeGame.Game game = new SnakeGame.Game(1L);
        check(game.length() == 3, "蛇的初始长度应为 3");
        check(game.snake.peekFirst().equals(new Point(10, 10)), "蛇头应位于棋盘中央");
        check(game.direction == SnakeGame.Direction.RIGHT, "蛇应初始朝右");
        check(game.score == 0, "初始得分应为 0");
        check(!game.over, "初始状态不应结束");
        check(game.food != null, "初始应有食物");
        check(!game.isSnake(game.food.x, game.food.y), "食物不应出现在蛇身上");
    }

    private static void movesOneCell() {
        SnakeGame.Game game = new SnakeGame.Game(2L);
        game.step();
        check(game.snake.peekFirst().equals(new Point(11, 10)), "每一步蛇头应前进一格");
        check(game.length() == 3, "未吃到食物时长度不变");
        check(game.ticks == 1, "tick 计数应递增");
    }

    private static void eatsFoodAndGrows() {
        SnakeGame.Game game = new SnakeGame.Game(3L);
        game.food = new Point(11, 10);
        game.step();

        check(game.score == 1, "吃到食物得分 +1");
        check(game.length() == 4, "吃到食物长度 +1");
        check(game.snake.peekFirst().equals(new Point(11, 10)), "蛇头应移动到食物格");
        check(!game.over, "吃到食物不应结束游戏");
        check(game.food != null, "吃到食物后应生成新食物");
        check(!game.isSnake(game.food.x, game.food.y), "新食物不应出现在蛇身上");
    }

    private static void rejectsReverseDirection() {
        SnakeGame.Game game = new SnakeGame.Game(4L);
        check(!game.turn(SnakeGame.Direction.LEFT), "朝右时不应接受向左的反向输入");

        game.step();
        check(game.snake.peekFirst().equals(new Point(11, 10)), "被拒绝的反向输入不应生效");

        check(game.turn(SnakeGame.Direction.UP), "向上转向应被接受");
        game.step();
        check(game.snake.peekFirst().equals(new Point(11, 9)), "转向后应按新方向移动");
    }

    private static void wallCollisionEndsGame() {
        SnakeGame.Game game = new SnakeGame.Game(5L);
        game.food = new Point(0, 0);
        check(game.turn(SnakeGame.Direction.UP), "向上转向应被接受");

        for (int i = 0; i < 20 && !game.over; i++) {
            game.step();
        }

        check(game.over, "撞墙后游戏应结束");
        check(game.snake.peekFirst().equals(new Point(10, 0)), "撞墙前蛇头应停在边界格内");
    }

    private static void selfCollisionEndsGame() {
        SnakeGame.Game game = new SnakeGame.Game(6L);
        game.snake.clear();
        // 头 (5,5)，向下移动会撞到身体 (5,6)。
        game.snake.addLast(new Point(5, 5));
        game.snake.addLast(new Point(4, 5));
        game.snake.addLast(new Point(4, 6));
        game.snake.addLast(new Point(5, 6));
        game.snake.addLast(new Point(6, 6));
        game.direction = SnakeGame.Direction.DOWN;
        game.nextDirection = SnakeGame.Direction.DOWN;
        game.food = new Point(0, 0);

        game.step();

        check(game.over, "撞到自己后游戏应结束");
    }

    private static void movingIntoFreeingTailIsAllowed() {
        SnakeGame.Game game = new SnakeGame.Game(7L);
        game.snake.clear();
        // 头 (5,5) 向右移动，目标格 (6,5) 正好是本 tick 会离开的尾巴。
        game.snake.addLast(new Point(5, 5));
        game.snake.addLast(new Point(5, 6));
        game.snake.addLast(new Point(6, 6));
        game.snake.addLast(new Point(6, 5));
        game.direction = SnakeGame.Direction.RIGHT;
        game.nextDirection = SnakeGame.Direction.RIGHT;
        game.food = new Point(0, 0);

        game.step();

        check(!game.over, "移入正在离开的尾巴格不应判定为撞自己");
        check(game.snake.peekFirst().equals(new Point(6, 5)), "蛇头应移动到尾巴原来的位置");
        check(game.length() == 4, "长度应保持不变");
    }

    private static void foodStaysOffSnake() {
        for (long seed = 0; seed < 50; seed++) {
            SnakeGame.Game game = new SnakeGame.Game(seed);
            check(game.food != null, "种子 " + seed + " 应生成食物");
            check(!game.isSnake(game.food.x, game.food.y),
                    "种子 " + seed + " 的食物不应在蛇身上");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("检查失败：" + message);
        }
    }
}
