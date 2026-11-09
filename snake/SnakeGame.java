import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 简易贪吃蛇游戏：仅使用 Java 标准库（Swing / AWT），无需第三方依赖。
 *
 * <p>编译与运行：
 * <pre>
 *   javac SnakeGame.java
 *   java SnakeGame
 * </pre>
 *
 * <p>操作：方向键转向，空格暂停 / 继续；开始界面按空格或 Enter 开始，
 * 游戏结束后按空格或 Enter 重新开始。
 */
public final class SnakeGame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** 网格边长（20 × 20）。 */
    static final int GRID_SIZE = 20;

    /** 每个格子的像素大小。 */
    static final int CELL_SIZE = 28;

    /** 棋盘像素大小。 */
    static final int BOARD_SIZE = GRID_SIZE * CELL_SIZE;

    /** 顶部得分栏高度。 */
    static final int HUD_HEIGHT = 58;

    /** 窗口内容区域大小。 */
    static final int WINDOW_WIDTH = BOARD_SIZE;
    static final int WINDOW_HEIGHT = BOARD_SIZE + HUD_HEIGHT;

    /** 蛇每移动一格的时间间隔（毫秒）。 */
    static final int TICK_MS = 130;

    /** 游戏所处的界面状态。 */
    enum State {
        START,
        RUNNING,
        PAUSED,
        GAME_OVER
    }

    /** 四个移动方向。 */
    enum Direction {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        final int dx;
        final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        /** 判断两个方向是否正好相反（蛇不能 180° 掉头）。 */
        boolean isOpposite(Direction other) {
            return dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    /**
     * 纯游戏状态，不依赖 Swing，方便在无图形界面环境下做规则自测。
     *
     * <p>字段使用包可见性，仅为同一目录下的 {@link SnakeGameTest} 与截图工具读取和构造
     * 测试场景；游戏运行时不直接依赖这些字段。
     */
    static final class Game {

        /** 蛇身格子，队首是蛇头。 */
        final Deque<Point> snake = new ArrayDeque<Point>();

        final Random random;

        /** 当前食物位置；棋盘被填满时为 null。 */
        Point food;

        /** 当前移动方向。 */
        Direction direction = Direction.RIGHT;

        /** 已在键盘上输入、等待下一 tick 生效的方向。 */
        Direction nextDirection = Direction.RIGHT;

        int score;
        int ticks;
        boolean over;
        boolean won;

        Game() {
            this(new Random());
        }

        Game(long seed) {
            this(new Random(seed));
        }

        Game(Random random) {
            this.random = random;
            reset();
        }

        /** 重置为初始局面：蛇长 3，位于棋盘中央并朝右。 */
        void reset() {
            snake.clear();
            int centerX = GRID_SIZE / 2;
            int centerY = GRID_SIZE / 2;
            snake.addFirst(new Point(centerX, centerY));
            snake.addLast(new Point(centerX - 1, centerY));
            snake.addLast(new Point(centerX - 2, centerY));

            direction = Direction.RIGHT;
            nextDirection = Direction.RIGHT;
            score = 0;
            ticks = 0;
            over = false;
            won = false;
            spawnFood();
        }

        /**
         * 记录转向请求。反向输入会被忽略，返回是否接受。
         * 方向在下一个 tick 才真正生效，避免一次 tick 内连续转向导致自撞。
         */
        boolean turn(Direction next) {
            if (over || next == null || next.isOpposite(direction)) {
                return false;
            }
            nextDirection = next;
            return true;
        }

        /** 前进一个 tick：移动、吃食物、检测碰撞。 */
        void step() {
            if (over) {
                return;
            }

            direction = nextDirection;
            Point head = snake.peekFirst();
            Point newHead = new Point(head.x + direction.dx, head.y + direction.dy);

            // 撞墙：不进入棋盘外，直接结束。
            if (newHead.x < 0 || newHead.x >= GRID_SIZE
                    || newHead.y < 0 || newHead.y >= GRID_SIZE) {
                over = true;
                return;
            }

            boolean eating = newHead.equals(food);
            // 尾巴会在本 tick 离开它所在的格子，因此移入尾巴格不算撞到自己。
            boolean movesIntoFreeingTail = !eating && newHead.equals(snake.peekLast());
            if (snake.contains(newHead) && !movesIntoFreeingTail) {
                over = true;
                return;
            }

            snake.addFirst(newHead);
            if (eating) {
                score++;
                if (snake.size() == GRID_SIZE * GRID_SIZE) {
                    // 棋盘填满，没有空位再放食物。
                    won = true;
                    over = true;
                    food = null;
                } else {
                    spawnFood();
                }
            } else {
                snake.removeLast();
            }
            ticks++;
        }

        /** 在空白格中随机放置食物。 */
        private void spawnFood() {
            int freeCount = GRID_SIZE * GRID_SIZE - snake.size();
            if (freeCount <= 0) {
                food = null;
                return;
            }
            int pick = random.nextInt(freeCount);
            for (int y = 0; y < GRID_SIZE; y++) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    if (!isSnake(x, y)) {
                        if (pick == 0) {
                            food = new Point(x, y);
                            return;
                        }
                        pick--;
                    }
                }
            }
            food = null;
        }

        /** 指定格子是否被蛇占用。 */
        boolean isSnake(int x, int y) {
            for (Point point : snake) {
                if (point.x == x && point.y == y) {
                    return true;
                }
            }
            return false;
        }

        int length() {
            return snake.size();
        }
    }

    private SnakeGame() {
        super("贪吃蛇 · Snake");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        final GamePanel panel = new GamePanel();
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                panel.requestFocusInWindow();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SnakeGame().setVisible(true);
            }
        });
    }
}

/**
 * 游戏画布：负责绘制棋盘、得分栏和开始 / 暂停 / 结束覆盖层。
 *
 * <p>与 {@link SnakeGame.Game} 分开，让游戏规则可以在没有窗口的环境中单独测试。
 */
final class GamePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BACKGROUND = new Color(8, 14, 28);
    private static final Color HUD_BG = new Color(12, 21, 39);
    private static final Color HUD_BORDER = new Color(30, 47, 76);
    private static final Color BOARD_BG = new Color(13, 23, 42);
    private static final Color BOARD_ALT = new Color(16, 28, 50);
    private static final Color GRID_COLOR = new Color(39, 58, 91, 120);
    private static final Color SNAKE_HEAD = new Color(74, 222, 128);
    private static final Color SNAKE_TAIL = new Color(22, 101, 52);
    private static final Color FOOD = new Color(251, 113, 133);
    private static final Color TEXT = new Color(226, 232, 240);
    private static final Color MUTED = new Color(148, 163, 184);
    private static final Color ACCENT = new Color(74, 222, 128);
    private static final Color CARD_BG = new Color(15, 25, 45, 248);
    private static final Color DARK_TEXT = new Color(6, 20, 13);

    // 这些字段在截图工具与测试中可直接设置，以复现固定局面。
    SnakeGame.State state = SnakeGame.State.START;
    transient SnakeGame.Game game = new SnakeGame.Game();
    int bestScore;
    boolean newRecord;

    private final Timer timer;
    /** 空格是否仍被按住，用于过滤系统按键重复事件。 */
    private boolean spaceHeld;

    GamePanel() {
        setPreferredSize(new Dimension(SnakeGame.WINDOW_WIDTH, SnakeGame.WINDOW_HEIGHT));
        setBackground(BACKGROUND);
        setFocusable(true);
        setDoubleBuffered(true);

        timer = new Timer(SnakeGame.TICK_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onTick();
            }
        });
        timer.setCoalesce(true);
        installKeyBindings();
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // 失焦时可能收不到按键释放事件，重置按住状态，避免空格失效。
                spaceHeld = false;
            }
        });
    }

    /** 把方向键和空格 / Enter 绑定到窗口级动作。 */
    private void installKeyBindings() {
        bind(KeyEvent.VK_UP, "turnUp", new Runnable() {
            @Override
            public void run() {
                turn(SnakeGame.Direction.UP);
            }
        });
        bind(KeyEvent.VK_DOWN, "turnDown", new Runnable() {
            @Override
            public void run() {
                turn(SnakeGame.Direction.DOWN);
            }
        });
        bind(KeyEvent.VK_LEFT, "turnLeft", new Runnable() {
            @Override
            public void run() {
                turn(SnakeGame.Direction.LEFT);
            }
        });
        bind(KeyEvent.VK_RIGHT, "turnRight", new Runnable() {
            @Override
            public void run() {
                turn(SnakeGame.Direction.RIGHT);
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "spacePressed", new Runnable() {
            @Override
            public void run() {
                onSpacePressed();
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "spaceReleased",
                new Runnable() {
                    @Override
                    public void run() {
                        spaceHeld = false;
                    }
                });
        bind(KeyEvent.VK_ENTER, "enterAction", new Runnable() {
            @Override
            public void run() {
                onEnter();
            }
        });
    }

    private void bind(int keyCode, String actionName, Runnable action) {
        bind(KeyStroke.getKeyStroke(keyCode, 0), actionName, action);
    }

    private void bind(KeyStroke keyStroke, String actionName, final Runnable action) {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        inputMap.put(keyStroke, actionName);
        actionMap.put(actionName, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void turn(SnakeGame.Direction direction) {
        // 暂停和结束状态下不接受转向，避免恢复后意外掉头。
        if (state == SnakeGame.State.RUNNING && game.turn(direction)) {
            repaint();
        }
    }

    private void onSpacePressed() {
        // 系统会自动重复 keyPressed；空格只在第一次按下时生效，松开后重置。
        if (spaceHeld) {
            return;
        }
        spaceHeld = true;

        switch (state) {
            case START:
            case GAME_OVER:
                startGame();
                break;
            case RUNNING:
                pauseGame();
                break;
            case PAUSED:
                resumeGame();
                break;
            default:
                break;
        }
    }

    private void onEnter() {
        if (state == SnakeGame.State.START || state == SnakeGame.State.GAME_OVER) {
            startGame();
        }
    }

    private void startGame() {
        game = new SnakeGame.Game();
        state = SnakeGame.State.RUNNING;
        newRecord = false;
        timer.start();
        requestFocusInWindow();
        repaint();
    }

    private void pauseGame() {
        if (state == SnakeGame.State.RUNNING) {
            state = SnakeGame.State.PAUSED;
            timer.stop();
            repaint();
        }
    }

    private void resumeGame() {
        if (state == SnakeGame.State.PAUSED) {
            state = SnakeGame.State.RUNNING;
            timer.start();
            requestFocusInWindow();
            repaint();
        }
    }

    private void onTick() {
        if (state != SnakeGame.State.RUNNING) {
            return;
        }
        game.step();
        // 最高分实时刷新，HUD 不会低于本局当前得分。
        if (game.score > bestScore) {
            bestScore = game.score;
            newRecord = true;
        }
        if (game.over) {
            state = SnakeGame.State.GAME_OVER;
            timer.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            paintHud(g);
            paintBoard(g);

            if (state == SnakeGame.State.START) {
                paintStartOverlay(g);
            } else if (state == SnakeGame.State.PAUSED) {
                paintPauseOverlay(g);
            } else if (state == SnakeGame.State.GAME_OVER) {
                paintGameOverOverlay(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintHud(Graphics2D g) {
        g.setColor(HUD_BG);
        g.fillRect(0, 0, getWidth(), SnakeGame.HUD_HEIGHT);
        g.setColor(HUD_BORDER);
        g.drawLine(0, SnakeGame.HUD_HEIGHT - 1, getWidth(), SnakeGame.HUD_HEIGHT - 1);

        g.setFont(font(Font.BOLD, 20));
        g.setColor(TEXT);
        g.drawString("贪吃蛇", 22, 37);

        g.setFont(font(Font.PLAIN, 10));
        g.setColor(MUTED);
        g.drawString("SNAKE", 94, 36);

        drawStat(g, "得分", String.valueOf(game.score), getWidth() - 162);
        drawStat(g, "最高分", String.valueOf(bestScore), getWidth() - 24);
    }

    private void drawStat(Graphics2D g, String label, String value, int rightX) {
        Font labelFont = font(Font.PLAIN, 11);
        Font valueFont = font(Font.BOLD, 19);
        FontMetrics labelMetrics = g.getFontMetrics(labelFont);
        FontMetrics valueMetrics = g.getFontMetrics(valueFont);

        g.setFont(labelFont);
        g.setColor(MUTED);
        g.drawString(label, rightX - labelMetrics.stringWidth(label), 23);

        g.setFont(valueFont);
        g.setColor(TEXT);
        g.drawString(value, rightX - valueMetrics.stringWidth(value), 47);
    }

    private void paintBoard(Graphics2D g) {
        int boardTop = SnakeGame.HUD_HEIGHT;
        g.setColor(BOARD_BG);
        g.fillRect(0, boardTop, getWidth(), SnakeGame.BOARD_SIZE);

        // 交错的深色格子让 20 × 20 网格更容易辨认。
        g.setColor(BOARD_ALT);
        for (int y = 0; y < SnakeGame.GRID_SIZE; y++) {
            for (int x = 0; x < SnakeGame.GRID_SIZE; x++) {
                if (((x + y) & 1) == 0) {
                    g.fillRect(x * SnakeGame.CELL_SIZE,
                            boardTop + y * SnakeGame.CELL_SIZE,
                            SnakeGame.CELL_SIZE, SnakeGame.CELL_SIZE);
                }
            }
        }

        g.setColor(GRID_COLOR);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= SnakeGame.GRID_SIZE; i++) {
            int offset = i * SnakeGame.CELL_SIZE;
            g.drawLine(0, boardTop + offset, getWidth(), boardTop + offset);
            g.drawLine(offset, boardTop, offset, boardTop + SnakeGame.BOARD_SIZE);
        }

        paintFood(g);
        paintSnake(g);
    }

    private void paintFood(Graphics2D g) {
        if (game.food == null) {
            return;
        }
        int centerX = game.food.x * SnakeGame.CELL_SIZE + SnakeGame.CELL_SIZE / 2;
        int centerY = SnakeGame.HUD_HEIGHT + game.food.y * SnakeGame.CELL_SIZE
                + SnakeGame.CELL_SIZE / 2;

        // 光晕随 tick 轻微呼吸，让食物更醒目。
        float pulse = (float) (0.5 + 0.5 * Math.sin(game.ticks * 0.9));
        int glowRadius = 11 + Math.round(pulse * 3f);
        g.setColor(new Color(251, 113, 133, 30));
        g.fillOval(centerX - glowRadius, centerY - glowRadius,
                glowRadius * 2, glowRadius * 2);
        g.setColor(new Color(251, 113, 133, 65));
        g.fillOval(centerX - 9, centerY - 9, 18, 18);

        g.setColor(FOOD);
        g.fillOval(centerX - 7, centerY - 7, 14, 14);
        g.setColor(new Color(255, 255, 255, 170));
        g.fillOval(centerX - 4, centerY - 5, 4, 4);
    }

    private void paintSnake(Graphics2D g) {
        List<Point> cells = new ArrayList<Point>(game.snake);
        int size = cells.size();
        // 从尾巴画到蛇头，保证蛇头的眼睛位于最上层。
        for (int index = size - 1; index >= 0; index--) {
            Point point = cells.get(index);
            float ratio = size <= 1 ? 0f : (float) index / (size - 1);
            Color bodyColor = mix(SNAKE_HEAD, SNAKE_TAIL, ratio);
            int x = point.x * SnakeGame.CELL_SIZE;
            int y = SnakeGame.HUD_HEIGHT + point.y * SnakeGame.CELL_SIZE;
            int inset = index == 0 ? 1 : 2;

            g.setColor(new Color(0, 0, 0, 55));
            g.fillRoundRect(x + inset + 1, y + inset + 2,
                    SnakeGame.CELL_SIZE - inset * 2, SnakeGame.CELL_SIZE - inset * 2,
                    10, 10);

            g.setColor(bodyColor);
            g.fillRoundRect(x + inset, y + inset,
                    SnakeGame.CELL_SIZE - inset * 2, SnakeGame.CELL_SIZE - inset * 2,
                    10, 10);

            if (index == 0) {
                // 头部高光，增加一点立体感。
                g.setColor(new Color(255, 255, 255, 50));
                g.fillRoundRect(x + 6, y + 6, SnakeGame.CELL_SIZE - 12, 6, 6, 6);
                paintEyes(g, x, y);
            }
        }
    }

    private void paintEyes(Graphics2D g, int headX, int headY) {
        SnakeGame.Direction facing = state == SnakeGame.State.RUNNING
                ? game.nextDirection : game.direction;
        int centerX = headX + SnakeGame.CELL_SIZE / 2;
        int centerY = headY + SnakeGame.CELL_SIZE / 2;
        int eyeX = centerX + facing.dx * 3;
        int eyeY = centerY + facing.dy * 3;

        if (facing == SnakeGame.Direction.LEFT || facing == SnakeGame.Direction.RIGHT) {
            drawEye(g, eyeX, eyeY - 5, facing);
            drawEye(g, eyeX, eyeY + 5, facing);
        } else {
            drawEye(g, eyeX - 5, eyeY, facing);
            drawEye(g, eyeX + 5, eyeY, facing);
        }
    }

    private void drawEye(Graphics2D g, int centerX, int centerY, SnakeGame.Direction facing) {
        g.setColor(new Color(248, 250, 252));
        g.fillOval(centerX - 3, centerY - 3, 7, 7);
        g.setColor(new Color(15, 23, 42));
        g.fillOval(centerX - 1 + facing.dx * 2, centerY - 1 + facing.dy * 2, 3, 3);
    }

    private void paintStartOverlay(Graphics2D g) {
        paintFullBackdrop(g);

        int cardWidth = 440;
        int cardHeight = 372;
        int cardX = (getWidth() - cardWidth) / 2;
        int cardY = (getHeight() - cardHeight) / 2;
        drawCard(g, cardX, cardY, cardWidth, cardHeight);
        int centerX = getWidth() / 2;

        drawCentered(g, "贪吃蛇", centerX, cardY + 92, font(Font.BOLD, 44), TEXT);
        drawCentered(g, "SNAKE · 20 × 20", centerX, cardY + 120,
                font(Font.PLAIN, 12), MUTED);

        g.setColor(new Color(51, 65, 85));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cardX + 48, cardY + 142, cardX + cardWidth - 48, cardY + 142);

        drawCentered(g, "方向键  控制方向", centerX, cardY + 178,
                font(Font.PLAIN, 15), TEXT);
        drawCentered(g, "空格  暂停 / 继续", centerX, cardY + 206,
                font(Font.PLAIN, 15), TEXT);
        drawCentered(g, "吃到食物 +1 分，撞墙或撞到自己结束", centerX, cardY + 240,
                font(Font.PLAIN, 13), MUTED);

        int pillWidth = 292;
        int pillHeight = 48;
        int pillX = centerX - pillWidth / 2;
        int pillY = cardY + 272;
        g.setColor(ACCENT);
        g.fillRoundRect(pillX, pillY, pillWidth, pillHeight, pillHeight, pillHeight);
        drawCentered(g, "按 空格 开始游戏", centerX, pillY + 31,
                font(Font.BOLD, 17), DARK_TEXT);
        drawCentered(g, "Enter 也可以开始", centerX, cardY + 348,
                font(Font.PLAIN, 11), MUTED);
    }

    private void paintPauseOverlay(Graphics2D g) {
        g.setColor(new Color(4, 9, 20, 185));
        g.fillRect(0, SnakeGame.HUD_HEIGHT, getWidth(), SnakeGame.BOARD_SIZE);

        int cardWidth = 320;
        int cardHeight = 172;
        int cardX = (getWidth() - cardWidth) / 2;
        int cardY = SnakeGame.HUD_HEIGHT + (SnakeGame.BOARD_SIZE - cardHeight) / 2;
        drawCard(g, cardX, cardY, cardWidth, cardHeight);
        int centerX = getWidth() / 2;

        drawCentered(g, "已暂停", centerX, cardY + 76, font(Font.BOLD, 32), TEXT);
        drawCentered(g, "按 空格 继续游戏", centerX, cardY + 118,
                font(Font.PLAIN, 14), MUTED);
    }

    private void paintGameOverOverlay(Graphics2D g) {
        paintFullBackdrop(g);

        int cardWidth = 400;
        int cardHeight = 340;
        int cardX = (getWidth() - cardWidth) / 2;
        int cardY = (getHeight() - cardHeight) / 2;
        drawCard(g, cardX, cardY, cardWidth, cardHeight);
        int centerX = getWidth() / 2;

        drawCentered(g, game.won ? "通关！" : "游戏结束", centerX, cardY + 62,
                font(Font.BOLD, 34), game.won ? ACCENT : TEXT);
        drawCentered(g, "最终得分", centerX, cardY + 96,
                font(Font.PLAIN, 13), MUTED);
        drawCentered(g, String.valueOf(game.score), centerX, cardY + 160,
                font(Font.BOLD, 62), ACCENT);
        drawCentered(g, newRecord ? "新纪录！最高分 " + bestScore : "最高分 " + bestScore,
                centerX, cardY + 194, font(Font.PLAIN, 13),
                newRecord ? ACCENT : MUTED);

        int pillWidth = 280;
        int pillHeight = 46;
        int pillX = centerX - pillWidth / 2;
        int pillY = cardY + 222;
        g.setColor(new Color(30, 41, 59));
        g.fillRoundRect(pillX, pillY, pillWidth, pillHeight, pillHeight, pillHeight);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(pillX, pillY, pillWidth, pillHeight, pillHeight, pillHeight);
        drawCentered(g, "按 空格 重新开始", centerX, pillY + 30,
                font(Font.BOLD, 15), TEXT);
        drawCentered(g, "Enter 也可以重新开始", centerX, cardY + 308,
                font(Font.PLAIN, 11), MUTED);
    }

    /** 开始 / 结束界面使用不透明渐变背景，避免透出后面的棋盘和得分栏。 */
    private void paintFullBackdrop(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(11, 20, 38),
                0, getHeight(), new Color(5, 9, 19)));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private static void drawCard(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(x + 3, y + 5, width, height, 24, 24);

        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, width, height, 24, 24);
        g.setColor(CARD_BG);
        g.fill(shape);
        g.setColor(new Color(74, 222, 128, 80));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baseline,
                                     Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baseline);
    }

    private static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** 按比例混合两种颜色，用于蛇身从头部到尾部的渐变。 */
    private static Color mix(Color from, Color to, float ratio) {
        float t = Math.max(0f, Math.min(1f, ratio));
        int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        return new Color(red, green, blue);
    }
}
