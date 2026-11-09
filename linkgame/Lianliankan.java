package linkgame;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 简易连连看（Lianliankan / Link Game）。
 *
 * <p>使用 Java 标准库 Swing/AWT 实现，无第三方依赖，JDK 8 及以上可编译运行。
 * 整个游戏（界面 + 规则）都在本文件中，编译与启动方式：</p>
 *
 * <pre>
 * javac linkgame/Lianliankan.java
 * java linkgame.Lianliankan
 * </pre>
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>8 行 × 10 列棋盘，每格 50×50 像素，共 8 种图案，每种图案数量为偶数；</li>
 *   <li>点击两个相同图案，若能用横竖直线连接且拐点不超过 2 个，即可消除并 +10 分；</li>
 *   <li>路径允许绕到棋盘外侧，但不能穿过未消除的格子；</li>
 *   <li>每次消除后若剩余图案无可行解，会自动重排；通关或重排失败时给出提示。</li>
 * </ul>
 *
 * <p>代码中 {@link GameEngine} 只负责棋盘规则，{@link GamePanel} 只负责绘制与鼠标交互，
 * 两者分离便于在无图形环境下对核心逻辑做自检（见 {@code LianliankanTest.java}）。</p>
 */
public class Lianliankan extends JFrame {

    /** 棋盘行数。 */
    static final int ROWS = 8;
    /** 棋盘列数。 */
    static final int COLS = 10;
    /** 每格像素宽度 / 高度。 */
    static final int TILE = 50;
    /** 棋盘四周留给“绕行路径”的边距（至少为 TILE / 2，保证外圈路径可见）。 */
    static final int MARGIN = 30;
    /** 图案种类数，每种图案都会成对出现。 */
    static final int KIND_COUNT = 8;

    /** 顶部栏、状态栏等界面区域的底色。 */
    private static final Color SURFACE = new Color(0xFFFFFF);
    /** 状态文字默认颜色。 */
    private static final Color TEXT_MUTED = new Color(0x64748B);
    /** 操作成功时的状态文字颜色。 */
    private static final Color TEXT_SUCCESS = new Color(0x15803D);
    /** 操作失败时的状态文字颜色。 */
    private static final Color TEXT_ERROR = new Color(0xB91C1C);
    /** 自动重排提示颜色。 */
    private static final Color TEXT_NOTICE = new Color(0xB45309);
    /** 选中格子的描边颜色。 */
    private static final Color SELECTED_BORDER = new Color(0xF59E0B);
    /** 路径颜色。 */
    private static final Color PATH_COLOR = new Color(0xF97316);
    /** 默认操作提示。 */
    private static final String DEFAULT_TIP = "点击两个相同图案，路径不超过两个拐点即可消除";

    /**
     * 8 种图案的颜色。下标 0 不使用，棋盘上的 1..8 对应这里的颜色。
     * 形状在 {@link #drawSymbol(Graphics2D, int, int, int, int)} 中绘制。
     */
    private static final Color[] KIND_COLORS = {
            null,
            new Color(0xEF4444), // 1 圆形：红
            new Color(0xF59E0B), // 2 五角星：橙黄
            new Color(0x3B82F6), // 3 菱形：蓝
            new Color(0x10B981), // 4 三角形：绿
            new Color(0x8B5CF6), // 5 圆角方块：紫
            new Color(0xEC4899), // 6 爱心：粉
            new Color(0x14B8A6), // 7 十字：青
            new Color(0x6366F1)  // 8 六边形：靛蓝
    };

    /** 优先选用能较好显示中文的字体，找不到时退回逻辑字体 SansSerif。 */
    private static final String UI_FONT_FAMILY = pickFontFamily();

    private final GameEngine engine;
    private final GamePanel gamePanel;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private JButton restartButton;

    public Lianliankan() {
        super("连连看 · Lianliankan");
        engine = new GameEngine();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildTopBar(), BorderLayout.NORTH);
        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        installKeyBindings();

        pack();
        setResizable(false);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                gamePanel.requestFocusInWindow();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // 系统外观不可用时使用 Swing 默认外观即可，不影响游戏逻辑。
                }
                new Lianliankan().setVisible(true);
            }
        });
    }

    // ---------------------------------------------------------------------
    // 界面搭建
    // ---------------------------------------------------------------------

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(SURFACE);
        bar.setBorder(new EmptyBorder(14, 20, 10, 20));
        bar.setPreferredSize(new Dimension(COLS * TILE + 2 * MARGIN, 72));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new javax.swing.BoxLayout(titleBox, javax.swing.BoxLayout.Y_AXIS));

        JLabel title = new JLabel("连连看");
        title.setFont(uiFont(Font.BOLD, 22));
        title.setForeground(new Color(0x1E293B));

        JLabel subtitle = new JLabel("连接两个相同图案，最多两个拐点");
        subtitle.setFont(uiFont(Font.PLAIN, 12));
        subtitle.setForeground(new Color(0x94A3B8));

        titleBox.add(title);
        titleBox.add(javax.swing.Box.createVerticalStrut(2));
        titleBox.add(subtitle);
        bar.add(titleBox, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        scoreLabel = new JLabel("得分 0");
        scoreLabel.setFont(uiFont(Font.BOLD, 17));
        scoreLabel.setForeground(new Color(0x334155));
        // 固定宽度，避免得分从 0 变成 10/100 时顶部栏重新布局造成文字被截断。
        scoreLabel.setPreferredSize(new Dimension(96, 28));
        scoreLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);

        restartButton = new RoundedButton("重新开始  R");
        restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restartGame();
            }
        });

        right.add(scoreLabel);
        right.add(restartButton);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(0xE8EDF5)),
                new EmptyBorder(8, 20, 9, 20)));
        bar.setPreferredSize(new Dimension(COLS * TILE + 2 * MARGIN, 38));

        statusLabel = new JLabel(DEFAULT_TIP);
        statusLabel.setFont(uiFont(Font.PLAIN, 13));
        statusLabel.setForeground(TEXT_MUTED);

        JLabel hint = new JLabel("R 重新开始 · Esc 取消选择");
        hint.setFont(uiFont(Font.PLAIN, 12));
        hint.setForeground(new Color(0xA8B3C4));

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    private void installKeyBindings() {
        JRootPane root = getRootPane();
        javax.swing.InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap actionMap = root.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "restartGame");
        actionMap.put("restartGame", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restartGame();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        actionMap.put("clearSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!gamePanel.isLocked()) {
                    engine.clearSelection();
                    gamePanel.repaint();
                    setStatus("已取消选择", TEXT_MUTED);
                }
            }
        });
    }

    // ---------------------------------------------------------------------
    // 游戏状态与动画
    // ---------------------------------------------------------------------

    private void restartGame() {
        stopAnimations();
        engine.newGame();
        gamePanel.resetVisualState();
        updateScoreLabel();
        setStatus(DEFAULT_TIP, TEXT_MUTED);
        gamePanel.repaint();
    }

    private void stopAnimations() {
        if (gamePanel.pathTimer != null) {
            gamePanel.pathTimer.stop();
            gamePanel.pathTimer = null;
        }
        if (gamePanel.flashTimer != null) {
            gamePanel.flashTimer.stop();
            gamePanel.flashTimer = null;
        }
    }

    private void updateScoreLabel() {
        scoreLabel.setText("得分 " + engine.getScore());
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private void showWinDialog() {
        JOptionPane.showMessageDialog(
                Lianliankan.this,
                "恭喜通关！\n最终得分：" + engine.getScore() + " 分",
                "通关",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showGameOverDialog() {
        JOptionPane.showMessageDialog(
                Lianliankan.this,
                "重排后仍无可行解，游戏结束。\n当前得分：" + engine.getScore() + " 分",
                "游戏结束",
                JOptionPane.WARNING_MESSAGE);
    }

    // ---------------------------------------------------------------------
    // 棋盘绘制与鼠标交互
    // ---------------------------------------------------------------------

    /**
     * 棋盘面板：负责绘制棋盘、图案、选中高亮和连接路径。
     * 所有绘制坐标都基于本面板，路径使用“带一圈外边框”的棋盘坐标。
     */
    private final class GamePanel extends JPanel {

        /** 当前正在展示的连接路径（带边框坐标，0..ROWS+1 / 0..COLS+1）。 */
        private List<GameEngine.Cell> activePath;
        /** 本次匹配的两个格子，用于在路径动画期间同时高亮。 */
        private GameEngine.Cell matchA;
        private GameEngine.Cell matchB;
        /** 不匹配 / 不可连通时短暂闪红的两个格子。 */
        private GameEngine.Cell flashA;
        private GameEngine.Cell flashB;
        /** 动画期间锁住鼠标输入，避免重复点击打乱状态。 */
        private boolean locked;
        /** 当前悬停的格子，-1 表示没有。 */
        private int hoverRow = -1;
        private int hoverCol = -1;
        /** 玩家点击后取消/消除时使用的定时器。 */
        private Timer pathTimer;
        private Timer flashTimer;

        GamePanel() {
            setPreferredSize(new Dimension(COLS * TILE + 2 * MARGIN, ROWS * TILE + 2 * MARGIN));
            setOpaque(true);
            setBackground(new Color(0xF6F8FD));
            setFocusable(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    int row = rowAt(e.getY());
                    int col = colAt(e.getX());
                    if (row >= 0 && col >= 0) {
                        onCellClicked(row, col);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverRow != -1 || hoverCol != -1) {
                        hoverRow = -1;
                        hoverCol = -1;
                        repaint();
                    }
                    setCursor(Cursor.getDefaultCursor());
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                }
            });
        }

        boolean isLocked() {
            return locked;
        }

        /** 重新开始或关闭动画时清理临时视觉状态。 */
        void resetVisualState() {
            activePath = null;
            matchA = null;
            matchB = null;
            flashA = null;
            flashB = null;
            locked = false;
            hoverRow = -1;
            hoverCol = -1;
            setCursor(Cursor.getDefaultCursor());
        }

        private void onCellClicked(int row, int col) {
            if (locked) {
                return;
            }

            GameEngine.ClickResult result = engine.click(row, col);
            switch (result.type) {
                case SELECTED:
                    setStatus("已选中一个图案，再选一个相同图案", TEXT_MUTED);
                    repaint();
                    break;
                case DESELECTED:
                    setStatus("已取消选择", TEXT_MUTED);
                    repaint();
                    break;
                case MISMATCH:
                    showTransientError(result,
                            "图案不同，无法消除");
                    break;
                case NO_PATH:
                    showTransientError(result,
                            "图案相同，但被其他图案挡住（最多两个拐点）");
                    break;
                case MATCH:
                    showMatch(result);
                    break;
                case IGNORED:
                default:
                    // 点到空格、动画期间的点击等直接忽略。
                    break;
            }
        }

        /** 不匹配或不可连通：两个格子短暂闪红后取消选择。 */
        private void showTransientError(GameEngine.ClickResult result, String message) {
            locked = true;
            flashA = result.first;
            flashB = result.second;
            setStatus(message, TEXT_ERROR);
            repaint();

            if (flashTimer != null) {
                flashTimer.stop();
            }
            flashTimer = new Timer(320, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    flashA = null;
                    flashB = null;
                    locked = false;
                    repaint();
                }
            });
            flashTimer.setRepeats(false);
            flashTimer.start();
        }

        /** 匹配成功：先绘制连接路径，再真正移除两个图案。 */
        private void showMatch(GameEngine.ClickResult result) {
            activePath = result.path;
            matchA = result.first;
            matchB = result.second;
            locked = true;
            updateScoreLabel();
            setStatus("连接成功！+10 分", TEXT_SUCCESS);
            repaint();

            if (pathTimer != null) {
                pathTimer.stop();
            }
            pathTimer = new Timer(340, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    finishMatch();
                }
            });
            pathTimer.setRepeats(false);
            pathTimer.start();
        }

        private void finishMatch() {
            activePath = null;
            matchA = null;
            matchB = null;
            locked = false;

            GameEngine.Completion completion = engine.completeMatch();
            updateScoreLabel();

            switch (completion) {
                case WON:
                    setStatus("恭喜通关！最终得分 " + engine.getScore() + " 分", TEXT_SUCCESS);
                    repaint();
                    showWinDialog();
                    break;
                case RESHUFFLED:
                    setStatus("没有可消除的组合，已自动重排剩余图案", TEXT_NOTICE);
                    repaint();
                    break;
                case GAME_OVER:
                    setStatus("重排后仍无可行解，游戏结束", TEXT_ERROR);
                    repaint();
                    showGameOverDialog();
                    break;
                case NORMAL:
                default:
                    setStatus("继续寻找下一对吧", TEXT_MUTED);
                    repaint();
                    break;
            }
        }

        private void updateHover(int x, int y) {
            int row = rowAt(y);
            int col = colAt(x);
            if (row != hoverRow || col != hoverCol) {
                hoverRow = row;
                hoverCol = col;
                repaint();
            }
            boolean overTile = row >= 0 && col >= 0 && engine.valueAt(row, col) != 0;
            setCursor(overTile ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
        }

        private int rowAt(int pixelY) {
            int offset = pixelY - MARGIN;
            if (offset < 0) {
                return -1;
            }
            int row = offset / TILE;
            return row < ROWS ? row : -1;
        }

        private int colAt(int pixelX) {
            int offset = pixelX - MARGIN;
            if (offset < 0) {
                return -1;
            }
            int col = offset / TILE;
            return col < COLS ? col : -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            paintBackground(g2);
            paintBoardBase(g2);
            paintCells(g2);
            paintPath(g2);

            g2.dispose();
        }

        /** 面板背景使用浅色渐变，让白底棋盘更有层次。 */
        private void paintBackground(Graphics2D g2) {
            GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(0xF8FAFF),
                    0, getHeight(), new Color(0xEDF2FA));
            g2.setPaint(gradient);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        /** 棋盘底板：白色圆角卡片 + 柔和阴影。 */
        private void paintBoardBase(Graphics2D g2) {
            int x = MARGIN - 10;
            int y = MARGIN - 10;
            int width = COLS * TILE + 20;
            int height = ROWS * TILE + 20;

            g2.setColor(new Color(15, 23, 42, 16));
            g2.fillRoundRect(x + 2, y + 4, width, height, 18, 18);

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, width, height, 18, 18);
            g2.setColor(new Color(0xE3EAF4));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, width, height, 18, 18);
        }

        /** 绘制所有格子：空格画成浅色占位块，有图案的格子画成彩色卡片。 */
        private void paintCells(Graphics2D g2) {
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int kind = engine.valueAt(row, col);
                    if (kind == 0) {
                        paintEmptySlot(g2, row, col);
                    } else {
                        paintTile(g2, row, col, kind);
                    }
                }
            }
        }

        private void paintEmptySlot(Graphics2D g2, int row, int col) {
            int x = MARGIN + col * TILE;
            int y = MARGIN + row * TILE;
            g2.setColor(new Color(0xF1F5FB));
            g2.fillRoundRect(x + 5, y + 5, TILE - 10, TILE - 10, 10, 10);
            g2.setColor(new Color(0xDCE4F0));
            g2.fillOval(x + TILE / 2 - 2, y + TILE / 2 - 2, 4, 4);
        }

        private void paintTile(Graphics2D g2, int row, int col, int kind) {
            int x = MARGIN + col * TILE + 3;
            int y = MARGIN + row * TILE + 3;
            int width = TILE - 6;
            int height = TILE - 6;
            int arc = 14;

            boolean selected = isSelectedCell(row, col);
            boolean flashed = isSameCell(flashA, row, col) || isSameCell(flashB, row, col);
            boolean hovered = hoverRow == row && hoverCol == col;
            Color base = KIND_COLORS[kind];

            Color fill;
            if (flashed) {
                fill = tint(new Color(0xEF4444), 0.78);
            } else if (selected) {
                fill = tint(base, 0.55);
            } else if (hovered) {
                fill = tint(base, 0.76);
            } else {
                fill = tint(base, 0.84);
            }
            Color border = flashed ? new Color(0xDC2626)
                    : selected ? SELECTED_BORDER
                    : tint(base, 0.30);

            // 选中时先画一圈柔和的金色光晕。
            if (selected && !flashed) {
                g2.setColor(new Color(245, 158, 11, 55));
                g2.setStroke(new BasicStroke(7f));
                g2.drawRoundRect(x - 2, y - 2, width + 4, height + 4, arc + 3, arc + 3);
            }

            // 卡片阴影。
            g2.setColor(new Color(15, 23, 42, selected ? 30 : 15));
            g2.fillRoundRect(x + 1, y + 3, width, height, arc, arc);

            // 卡片底色与描边。
            g2.setColor(fill);
            g2.fillRoundRect(x, y, width, height, arc, arc);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(selected ? 2.8f : 1.4f));
            g2.drawRoundRect(x, y, width, height, arc, arc);

            // 图案本体。
            drawSymbol(g2, kind, x + width / 2, y + height / 2, 24);
        }

        private boolean isSelectedCell(int row, int col) {
            return engine.isSelected(row, col)
                    || isSameCell(matchA, row, col)
                    || isSameCell(matchB, row, col);
        }

        private boolean isSameCell(GameEngine.Cell cell, int row, int col) {
            return cell != null && cell.row == row && cell.col == col;
        }

        /** 绘制连接路径：白色描边打底 + 橙色主线 + 拐点圆点。 */
        private void paintPath(Graphics2D g2) {
            if (activePath == null || activePath.size() < 2) {
                return;
            }

            int count = activePath.size();
            int[] xs = new int[count];
            int[] ys = new int[count];
            for (int i = 0; i < count; i++) {
                GameEngine.Cell cell = activePath.get(i);
                xs[i] = centerX(cell);
                ys[i] = centerY(cell);
            }

            g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 215));
            g2.drawPolyline(xs, ys, count);

            g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(PATH_COLOR);
            g2.drawPolyline(xs, ys, count);

            for (int i = 0; i < count; i++) {
                g2.setColor(Color.WHITE);
                g2.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
                g2.setColor(PATH_COLOR);
                g2.fillOval(xs[i] - 2, ys[i] - 2, 4, 4);
            }
        }

        /** 把带边框坐标换算成面板像素坐标。 */
        private int centerX(GameEngine.Cell cell) {
            return MARGIN + cell.col * TILE - TILE / 2;
        }

        private int centerY(GameEngine.Cell cell) {
            return MARGIN + cell.row * TILE - TILE / 2;
        }
    }

    // ---------------------------------------------------------------------
    // 绘制工具
    // ---------------------------------------------------------------------

    /** 根据图案编号绘制不同的简单几何图形。 */
    private static void drawSymbol(Graphics2D g, int kind, int cx, int cy, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(KIND_COLORS[kind]);
        int r = size / 2;

        switch (kind) {
            case 1: // 圆形
                g2.fillOval(cx - r, cy - r, size, size);
                break;
            case 2: // 五角星
                g2.fillPolygon(star(cx, cy, r, r * 0.45));
                break;
            case 3: // 菱形
                g2.fillPolygon(new int[]{cx, cx + r, cx, cx - r},
                        new int[]{cy - r, cy, cy + r, cy}, 4);
                break;
            case 4: // 三角形
                g2.fillPolygon(new int[]{cx, cx + r, cx - r},
                        new int[]{cy - r, cy + (int) (r * 0.8), cy + (int) (r * 0.8)}, 3);
                break;
            case 5: // 圆角方块
                g2.fillRoundRect(cx - r, cy - r, size, size, 6, 6);
                break;
            case 6: // 爱心
                g2.fill(heart(cx, cy, r));
                break;
            case 7: // 十字
                g2.fillRoundRect(cx - (int) (r * 0.32), cy - r,
                        (int) (r * 0.64), size, 4, 4);
                g2.fillRoundRect(cx - r, cy - (int) (r * 0.32),
                        size, (int) (r * 0.64), 4, 4);
                break;
            case 8: // 六边形
                g2.fillPolygon(regularPolygon(cx, cy, r, 6, -Math.PI / 2));
                break;
            default:
                g2.fillOval(cx - r, cy - r, size, size);
                break;
        }
        g2.dispose();
    }

    /** 生成 10 个顶点的五角星（外半径 outer，内半径 inner）。 */
    private static Polygon star(int cx, int cy, int outer, double inner) {
        int[] xs = new int[10];
        int[] ys = new int[10];
        double angle = -Math.PI / 2;
        for (int i = 0; i < 10; i++) {
            double radius = (i % 2 == 0) ? outer : inner;
            xs[i] = (int) Math.round(cx + Math.cos(angle) * radius);
            ys[i] = (int) Math.round(cy + Math.sin(angle) * radius);
            angle += Math.PI / 5;
        }
        return new Polygon(xs, ys, 10);
    }

    /** 生成正多边形顶点。 */
    private static Polygon regularPolygon(int cx, int cy, int radius, int sides, double startAngle) {
        int[] xs = new int[sides];
        int[] ys = new int[sides];
        for (int i = 0; i < sides; i++) {
            double angle = startAngle + 2 * Math.PI * i / sides;
            xs[i] = (int) Math.round(cx + Math.cos(angle) * radius);
            ys[i] = (int) Math.round(cy + Math.sin(angle) * radius);
        }
        return new Polygon(xs, ys, sides);
    }

    /** 用三次贝塞尔曲线绘制一个爱心。 */
    private static Path2D heart(int cx, int cy, int r) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(cx, cy + r * 0.85);
        path.curveTo(cx - r * 1.35, cy - r * 0.15,
                cx - r * 0.85, cy - r * 1.15,
                cx, cy - r * 0.42);
        path.curveTo(cx + r * 0.85, cy - r * 1.15,
                cx + r * 1.35, cy - r * 0.15,
                cx, cy + r * 0.85);
        path.closePath();
        return path;
    }

    /** 把颜色向白色混合：whiteRatio 越大，结果越浅。 */
    private static Color tint(Color color, double whiteRatio) {
        double keep = 1.0 - whiteRatio;
        int r = (int) Math.round(color.getRed() * keep + 255 * whiteRatio);
        int g = (int) Math.round(color.getGreen() * keep + 255 * whiteRatio);
        int b = (int) Math.round(color.getBlue() * keep + 255 * whiteRatio);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Font uiFont(int style, int size) {
        return new Font(UI_FONT_FAMILY, style, size);
    }

    /** 在常见中文字体中选一个可用的；都没有时使用逻辑字体。 */
    private static String pickFontFamily() {
        String[] candidates = {
                "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC",
                "Source Han Sans SC", "WenQuanYi Micro Hei"
        };
        try {
            Set<String> available = new HashSet<String>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String candidate : candidates) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // 某些无图形环境下可能无法枚举字体，忽略即可。
        }
        return "SansSerif";
    }

    /** 顶部栏的圆角按钮，避免不同系统外观下按钮样式差异过大。 */
    private static final class RoundedButton extends JButton {

        RoundedButton(String text) {
            super(text);
            setFont(uiFont(Font.BOLD, 13));
            setForeground(new Color(0x3B5BDB));
            setPreferredSize(new Dimension(120, 34));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color background = getModel().isRollover()
                    ? new Color(0xE0E7FF) : new Color(0xEEF2FF);
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(new Color(0xC7D2FE));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

            FontMetrics metrics = g2.getFontMetrics(getFont());
            int textX = (getWidth() - metrics.stringWidth(getText())) / 2;
            int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setColor(getForeground());
            g2.setFont(getFont());
            g2.drawString(getText(), textX, textY);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------------
    // 核心规则
    // ---------------------------------------------------------------------

    /**
     * 连连看游戏引擎：维护棋盘、选中状态、得分以及连通/重排/胜负判定。
     *
     * <p>棋盘坐标从 0 开始，0 表示空格，1..8 表示图案种类。为了支持路径绕到棋盘外侧，
     * 连通判定使用“带一圈边框”的坐标：真实格子 (r, c) 对应边框坐标 (r + 1, c + 1)，
     * 边框坐标范围是 0..ROWS+1 / 0..COLS+1，边框上的格子永远视为空格。</p>
     */
    public static class GameEngine {

        /** 一次点击的结果类型。 */
        public enum ClickType {
            /** 选中了一个格子。 */
            SELECTED,
            /** 再次点击同一格，取消选中。 */
            DESELECTED,
            /** 两个相同图案且可以连通。 */
            MATCH,
            /** 两个图案种类不同。 */
            MISMATCH,
            /** 图案相同但路径不满足“最多两个拐点”。 */
            NO_PATH,
            /** 无效点击（空格、动画中、游戏已结束等）。 */
            IGNORED
        }

        /** 消除完成后对整局状态的判断。 */
        public enum Completion {
            /** 一切正常，继续游戏。 */
            NORMAL,
            /** 棋盘清空，玩家胜利。 */
            WON,
            /** 原布局无解，已自动重排。 */
            RESHUFFLED,
            /** 重排后仍无解，游戏结束（正常重排实现下为兜底分支）。 */
            GAME_OVER
        }

        /** 棋盘坐标（行、列）。既用于真实格子，也用于带边框的路径坐标。 */
        public static final class Cell {
            public final int row;
            public final int col;

            public Cell(int row, int col) {
                this.row = row;
                this.col = col;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                if (!(other instanceof Cell)) {
                    return false;
                }
                Cell cell = (Cell) other;
                return row == cell.row && col == cell.col;
            }

            @Override
            public int hashCode() {
                return row * 31 + col;
            }

            @Override
            public String toString() {
                return "(" + row + "," + col + ")";
            }
        }

        /** 一次点击的详细结果，界面据此决定高亮、闪红或绘制路径。 */
        public static final class ClickResult {
            public final ClickType type;
            /** 第一个被点击的格子（可能为 null）。 */
            public final Cell first;
            /** 第二个被点击的格子（可能为 null）。 */
            public final Cell second;
            /** 匹配成功时的连接路径，使用带边框坐标。 */
            public final List<Cell> path;

            private ClickResult(ClickType type, Cell first, Cell second, List<Cell> path) {
                this.type = type;
                this.first = first;
                this.second = second;
                this.path = path;
            }

            private static ClickResult ignored() {
                return new ClickResult(ClickType.IGNORED, null, null, null);
            }
        }

        private final int[][] board = new int[ROWS][COLS];
        private final Random random;
        private int score;
        private Cell selected;
        private Cell pendingFirst;
        private Cell pendingSecond;
        private boolean won;
        private boolean gameOver;

        public GameEngine() {
            this(new Random());
        }

        /** 允许传入固定种子的 Random，方便复现布局和自检。 */
        GameEngine(Random random) {
            this.random = random;
            newGame();
        }

        /**
         * 开始新一局：生成 40 对图案并随机打乱，保证初始棋盘至少有一对可消。
         * 每种图案的数量都是偶数（8 种图案每种 10 个）。
         */
        public void newGame() {
            score = 0;
            won = false;
            gameOver = false;
            selected = null;
            pendingFirst = null;
            pendingSecond = null;

            List<Integer> pool = new ArrayList<Integer>(ROWS * COLS);
            for (int pair = 0; pair < ROWS * COLS / 2; pair++) {
                int kind = pair % KIND_COUNT + 1;
                pool.add(kind);
                pool.add(kind);
            }

            // 极少数随机布局可能一开始就无解，重试若干次；仍失败则强制构造一对相邻图案。
            for (int attempt = 0; attempt < 100; attempt++) {
                Collections.shuffle(pool, random);
                int index = 0;
                for (int row = 0; row < ROWS; row++) {
                    for (int col = 0; col < COLS; col++) {
                        board[row][col] = pool.get(index++);
                    }
                }
                if (hasAnyMove()) {
                    return;
                }
            }
            forceAdjacentPair();
        }

        /**
         * 点击一个格子并推进游戏状态。
         *
         * <p>第一次点击只选中；第二次点击同一格取消选中；否则比较两个图案。
         * 匹配成功时立即加分并返回连接路径，但格子要等界面播放完路径动画后
         * 调用 {@link #completeMatch()} 才真正移除。</p>
         */
        public ClickResult click(int row, int col) {
            if (won || gameOver || pendingFirst != null || !inBoard(row, col)) {
                return ClickResult.ignored();
            }
            if (board[row][col] == 0) {
                return ClickResult.ignored();
            }

            if (selected == null) {
                selected = new Cell(row, col);
                return new ClickResult(ClickType.SELECTED, selected, null, null);
            }

            if (selected.row == row && selected.col == col) {
                selected = null;
                return new ClickResult(ClickType.DESELECTED, new Cell(row, col), null, null);
            }

            Cell first = selected;
            Cell second = new Cell(row, col);

            if (board[first.row][first.col] != board[row][col]) {
                selected = null;
                return new ClickResult(ClickType.MISMATCH, first, second, null);
            }

            List<Cell> path = findPath(first.row, first.col, row, col);
            if (path == null) {
                selected = null;
                return new ClickResult(ClickType.NO_PATH, first, second, null);
            }

            // 保持选中状态直到动画结束，这样路径两端会同时高亮。
            pendingFirst = first;
            pendingSecond = second;
            score += 10;
            return new ClickResult(ClickType.MATCH, first, second, path);
        }

        /**
         * 动画结束后真正移除已匹配的两个格子，并判断胜利、自动重排或游戏结束。
         * 必须在 {@link #click(int, int)} 返回 MATCH 之后调用。
         */
        public Completion completeMatch() {
            if (pendingFirst == null) {
                return Completion.NORMAL;
            }

            board[pendingFirst.row][pendingFirst.col] = 0;
            board[pendingSecond.row][pendingSecond.col] = 0;
            pendingFirst = null;
            pendingSecond = null;
            selected = null;

            if (remainingTiles() == 0) {
                won = true;
                return Completion.WON;
            }

            if (!hasAnyMove()) {
                if (reshuffleRemaining()) {
                    return Completion.RESHUFFLED;
                }
                gameOver = true;
                return Completion.GAME_OVER;
            }
            return Completion.NORMAL;
        }

        /**
         * 查找两个格子之间“拐点不超过 2 个”的连接路径。
         *
         * <p>返回的路径点是带边框的棋盘坐标（0..ROWS+1 / 0..COLS+1），包含起点和终点，
         * 按顺序排列；找不到时返回 null。算法依次尝试：</p>
         * <ol>
         *   <li>直线：同一行/列且中间全为空；</li>
         *   <li>一个拐点：两个候选拐点 (r1, c2)、(r2, c1) 必须为空，且两段直线都无阻挡；</li>
         *   <li>两个拐点：枚举起点行/列上所有空格作为第一个拐点，再对第二个点做一次
         *       “最多一个拐点”的判定。</li>
         * </ol>
         */
        public List<Cell> findPath(int row1, int col1, int row2, int col2) {
            if (!inBoard(row1, col1) || !inBoard(row2, col2)) {
                return null;
            }

            Cell start = new Cell(row1 + 1, col1 + 1);
            Cell end = new Cell(row2 + 1, col2 + 1);

            // 1. 直线连接（0 个拐点）。
            if (straightClear(start, end)) {
                return Arrays.asList(start, end);
            }

            // 2. 一个拐点。
            Cell[] corners = {
                    new Cell(start.row, end.col),
                    new Cell(end.row, start.col)
            };
            for (Cell corner : corners) {
                if (isEmptyPadded(corner)
                        && straightClear(start, corner)
                        && straightClear(corner, end)) {
                    return Arrays.asList(start, corner, end);
                }
            }

            // 3. 两个拐点：先枚举起点行/列上的空格，再用“一个拐点”连到终点。
            for (int row = 0; row <= ROWS + 1; row++) {
                for (int col = 0; col <= COLS + 1; col++) {
                    Cell firstCorner = new Cell(row, col);
                    if (firstCorner.equals(start) || firstCorner.equals(end)
                            || !isEmptyPadded(firstCorner)) {
                        continue;
                    }
                    // 第一段必须是横线或竖线。
                    if (firstCorner.row != start.row && firstCorner.col != start.col) {
                        continue;
                    }
                    if (!straightClear(start, firstCorner)) {
                        continue;
                    }

                    // 某些一步就能连上的情况会在这里被覆盖，返回也无妨。
                    if (straightClear(firstCorner, end)) {
                        return Arrays.asList(start, firstCorner, end);
                    }

                    Cell[] secondCorners = {
                            new Cell(firstCorner.row, end.col),
                            new Cell(end.row, firstCorner.col)
                    };
                    for (Cell secondCorner : secondCorners) {
                        if (secondCorner.equals(start)
                                || secondCorner.equals(end)
                                || secondCorner.equals(firstCorner)) {
                            continue;
                        }
                        if (isEmptyPadded(secondCorner)
                                && straightClear(firstCorner, secondCorner)
                                && straightClear(secondCorner, end)) {
                            return Arrays.asList(start, firstCorner, secondCorner, end);
                        }
                    }
                }
            }
            return null;
        }

        /**
         * 判断当前棋盘是否还存在至少一对可消除的图案。
         * 每次消除后用它检测“死局”，决定是否自动重排。
         */
        public boolean hasAnyMove() {
            List<Cell> tiles = occupiedCells();
            for (int i = 0; i < tiles.size(); i++) {
                for (int j = i + 1; j < tiles.size(); j++) {
                    Cell a = tiles.get(i);
                    Cell b = tiles.get(j);
                    if (board[a.row][a.col] == board[b.row][b.col]
                            && findPath(a.row, a.col, b.row, b.col) != null) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * 重排剩余图案：先把剩余图案随机分配到原来的占用位置，最多尝试 100 次；
         * 若仍无可行解，则构造一对相邻的同图案作为兜底（相邻格必定可连通），
         * 因此正常游戏不会因为“重排后仍无解”而卡死。
         *
         * @return 是否成功得到一个至少存在一步可走的棋盘
         */
        boolean reshuffleRemaining() {
            List<Cell> positions = occupiedCells();
            List<Integer> values = new ArrayList<Integer>(positions.size());
            for (Cell cell : positions) {
                values.add(board[cell.row][cell.col]);
            }
            if (values.size() < 2) {
                return false;
            }

            for (int attempt = 0; attempt < 100; attempt++) {
                Collections.shuffle(values, random);
                for (int i = 0; i < positions.size(); i++) {
                    Cell cell = positions.get(i);
                    board[cell.row][cell.col] = values.get(i);
                }
                if (hasAnyMove()) {
                    return true;
                }
            }
            return forceAdjacentPair();
        }

        /**
         * 兜底重排：保留剩余图案的种类和数量，直接在上方两格放置一对相邻的同图案，
         * 其余图案随机填入其他位置。相邻两个同图案之间没有中间格，必定可以消除。
         */
        private boolean forceAdjacentPair() {
            List<Integer> values = new ArrayList<Integer>();
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    if (board[row][col] != 0) {
                        values.add(board[row][col]);
                    }
                }
            }
            if (values.size() < 2) {
                return false;
            }

            int[] counts = new int[KIND_COUNT + 1];
            for (int value : values) {
                counts[value]++;
            }
            int kind = -1;
            for (int candidate = 1; candidate <= KIND_COUNT; candidate++) {
                if (counts[candidate] >= 2) {
                    kind = candidate;
                    break;
                }
            }
            if (kind < 0) {
                return false;
            }

            int removed = 0;
            for (int i = 0; i < values.size() && removed < 2; i++) {
                if (values.get(i) == kind) {
                    values.remove(i);
                    i--;
                    removed++;
                }
            }

            List<Cell> slots = new ArrayList<Cell>(ROWS * COLS);
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    boolean reserved = (row == 0 && col == 0) || (row == 0 && col == 1);
                    if (!reserved) {
                        slots.add(new Cell(row, col));
                    }
                }
            }
            Collections.shuffle(values, random);
            Collections.shuffle(slots, random);

            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    board[row][col] = 0;
                }
            }
            board[0][0] = kind;
            board[0][1] = kind;
            for (int i = 0; i < values.size(); i++) {
                Cell slot = slots.get(i);
                board[slot.row][slot.col] = values.get(i);
            }

            // (0,0) 与 (0,1) 相邻且图案相同，必然存在可消的一步。
            return hasAnyMove();
        }

        /** 取消当前选中（游戏动画期间不处理）。 */
        public void clearSelection() {
            if (pendingFirst == null) {
                selected = null;
            }
        }

        public boolean isSelected(int row, int col) {
            return selected != null && selected.row == row && selected.col == col;
        }

        public int valueAt(int row, int col) {
            if (!inBoard(row, col)) {
                return 0;
            }
            return board[row][col];
        }

        public int getScore() {
            return score;
        }

        public boolean isWon() {
            return won;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public int remainingTiles() {
            int count = 0;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    if (board[row][col] != 0) {
                        count++;
                    }
                }
            }
            return count;
        }

        /** 仅供自检/测试：直接载入一个固定棋盘布局，并重置得分与选中状态。 */
        void loadBoardForTest(int[][] layout) {
            if (layout.length != ROWS || layout[0].length != COLS) {
                throw new IllegalArgumentException("测试棋盘必须是 " + ROWS + "×" + COLS);
            }
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int value = layout[row][col];
                    if (value < 0 || value > KIND_COUNT) {
                        throw new IllegalArgumentException("非法图案编号：" + value);
                    }
                    board[row][col] = value;
                }
            }
            score = 0;
            won = false;
            gameOver = false;
            selected = null;
            pendingFirst = null;
            pendingSecond = null;
        }

        private boolean inBoard(int row, int col) {
            return row >= 0 && row < ROWS && col >= 0 && col < COLS;
        }

        private List<Cell> occupiedCells() {
            List<Cell> cells = new ArrayList<Cell>();
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    if (board[row][col] != 0) {
                        cells.add(new Cell(row, col));
                    }
                }
            }
            return cells;
        }

        /** 判断带边框坐标上的格子是否为空；边框外永远不是空格。 */
        private boolean isEmptyPadded(Cell cell) {
            return isEmptyPadded(cell.row, cell.col);
        }

        private boolean isEmptyPadded(int row, int col) {
            if (row < 0 || row > ROWS + 1 || col < 0 || col > COLS + 1) {
                return false;
            }
            if (row == 0 || row == ROWS + 1 || col == 0 || col == COLS + 1) {
                return true;
            }
            return board[row - 1][col - 1] == 0;
        }

        /**
         * 判断两个坐标之间的直线段（不含端点）是否全为空。
         * 两个坐标必须同行或同列；端点本身不要求为空，方便连接两个图案格。
         */
        private boolean straightClear(Cell a, Cell b) {
            if (a.row == b.row) {
                int from = Math.min(a.col, b.col) + 1;
                int to = Math.max(a.col, b.col) - 1;
                for (int col = from; col <= to; col++) {
                    if (!isEmptyPadded(a.row, col)) {
                        return false;
                    }
                }
                return true;
            }
            if (a.col == b.col) {
                int from = Math.min(a.row, b.row) + 1;
                int to = Math.max(a.row, b.row) - 1;
                for (int row = from; row <= to; row++) {
                    if (!isEmptyPadded(row, a.col)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }
}
