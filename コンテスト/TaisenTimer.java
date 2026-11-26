import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

public class TaisenTimer extends JFrame {
    private static final Color PAGE_BG = Color.WHITE;
    private static final Color ACTIVE_BG = new Color(190, 31, 31);
    private static final Color ACTIVE_BORDER = new Color(237, 201, 37);
    private static final Color WAIT_BG = new Color(128, 128, 128);
    private static final Color WAIT_BORDER = new Color(60, 51, 8);
    private static final Color DARK = new Color(51, 51, 51);
    private static final Color RESET_RED = new Color(221, 51, 51);
    private static final Font UI_FONT = new Font("SansSerif", Font.PLAIN, 16);
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 16);
    private static final Font NAME_FONT = new Font("SansSerif", Font.BOLD, 48);
    private static final Font TIME_FONT = new Font(Font.MONOSPACED, Font.BOLD, 104);
    private static final Font STATE_FONT = new Font("SansSerif", Font.BOLD, 20);
    private final JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 999, 1));
    private final JSpinner secondsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3599, 1));
    private final PlayerCard[] playerCards = {
        new PlayerCard("A"),
        new PlayerCard("B")
    };
    private final RoundedButton pauseButton = new RoundedButton("一時停止", DARK, Color.WHITE);
    private final RoundedButton startAButton = new RoundedButton("Aから開始", ACTIVE_BG, Color.WHITE);
    private final RoundedButton startBButton = new RoundedButton("Bから開始", ACTIVE_BG, Color.WHITE);
    private double durationMilliseconds;
    private final double[] remainingMilliseconds = new double[2];
    private int activePlayer = -1;
    private boolean running = false;
    private long lastUpdateNanoseconds = System.nanoTime();
    private boolean fullscreen = false;
    private Rectangle normalBounds;

    public TaisenTimer() {
        super("対戦タイマー");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 520));
        setSize(1280, 720);
        setLocationRelativeTo(null);
        JPanel rootPanel = new JPanel(new BorderLayout(14, 14));
        rootPanel.setBackground(PAGE_BG);
        rootPanel.setBorder(new EmptyBorder(18, 18, 12, 18));
        setContentPane(rootPanel);
        rootPanel.add(createTopPanel(), BorderLayout.NORTH);
        rootPanel.add(createBoardPanel(), BorderLayout.CENTER);
        rootPanel.add(createHintLabel(), BorderLayout.SOUTH);
        durationMilliseconds = getConfiguredDuration();
        remainingMilliseconds[0] = durationMilliseconds;
        remainingMilliseconds[1] = durationMilliseconds;
        installKeyboardControls(rootPanel);
        Timer displayTimer = new Timer(25, event -> updateTimer());
        displayTimer.start();
        render();
    }

    private JPanel createTopPanel() {
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        timeRow.setOpaque(false);
        JLabel durationLabel = new JLabel("持ち時間");
        durationLabel.setFont(UI_FONT.deriveFont(Font.BOLD));
        JLabel minutesLabel = new JLabel("分");
        JLabel secondsLabel = new JLabel("秒");
        minutesLabel.setFont(UI_FONT);
        secondsLabel.setFont(UI_FONT);
        configureSpinner(minutesSpinner);
        configureSpinner(secondsSpinner);
        timeRow.add(durationLabel);
        timeRow.add(minutesSpinner);
        timeRow.add(minutesLabel);
        timeRow.add(secondsSpinner);
        timeRow.add(secondsLabel);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonRow.setOpaque(false);
        RoundedButton newButton = new RoundedButton("新しく開始", ACTIVE_BG, Color.WHITE);
        RoundedButton resetButton = new RoundedButton("リセット", RESET_RED, Color.WHITE);
        RoundedButton settingsButton = new RoundedButton("名前設定", DARK, Color.WHITE);
        newButton.addActionListener(event -> resetTimer());
        startAButton.addActionListener(event -> startFrom(0));
        startBButton.addActionListener(event -> startFrom(1));
        pauseButton.addActionListener(event -> togglePause());
        resetButton.addActionListener(event -> resetTimer());
        settingsButton.addActionListener(event -> showNameDialog());
        buttonRow.add(newButton);
        buttonRow.add(startAButton);
        buttonRow.add(startBButton);
        buttonRow.add(pauseButton);
        buttonRow.add(resetButton);
        buttonRow.add(settingsButton);
        container.add(timeRow);
        container.add(Box.createVerticalStrut(10));
        container.add(buttonRow);
        return container;
    }

    private JPanel createBoardPanel() {
        JPanel boardPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        boardPanel.setOpaque(false);
        boardPanel.add(playerCards[0]);
        boardPanel.add(playerCards[1]);
        playerCards[0].setClickAction(() -> {
            if (running && activePlayer == 0) {
                switchPlayer();
            }
        });
        playerCards[1].setClickAction(() -> {
            if (running && activePlayer == 1) {
                switchPlayer();
            }
        });
        return boardPanel;
    }

    private JLabel createHintLabel() {
        JLabel hintLabel = new JLabel(
            "Aから開始／Bから開始：最初のプレイヤーを選択　Space：手番交代　R：リセット　F：全画面　Esc：全画面解除",
            SwingConstants.CENTER
        );
        hintLabel.setFont(UI_FONT.deriveFont(13.0f));
        hintLabel.setForeground(new Color(85, 85, 85));
        return hintLabel;
    }

    private void configureSpinner(JSpinner spinner) {
        spinner.setFont(UI_FONT.deriveFont(18.0f));
        spinner.setPreferredSize(new Dimension(82, 38));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField textField = defaultEditor.getTextField();
            textField.setFont(UI_FONT.deriveFont(18.0f));
            textField.setHorizontalAlignment(SwingConstants.RIGHT);
            textField.setBackground(new Color(34, 34, 34));
            textField.setForeground(Color.WHITE);
            textField.setCaretColor(Color.WHITE);
            textField.setBorder(new EmptyBorder(5, 8, 5, 8));
        }
    }

    private void installKeyboardControls(JComponent rootPanel) {
        InputMap inputMap = rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPanel.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "space");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_DOWN_MASK), "shiftSpace");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "reset");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "fullscreen");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "leaveFullscreen");
        actionMap.put("space", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (running) {
                    switchPlayer();
                } else if (activePlayer == -1) {
                    startFrom(0);
                } else {
                    resumeTimer();
                }
            }
        });
        actionMap.put("shiftSpace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (running) {
                    switchPlayer();
                } else if (activePlayer == -1) {
                    startFrom(1);
                } else {
                    resumeTimer();
                }
            }
        });
        actionMap.put("reset", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                resetTimer();
            }
        });
        actionMap.put("fullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleFullscreen();
            }
        });
        actionMap.put("leaveFullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                leaveFullscreen();
            }
        });
    }

    private long getConfiguredDuration() {
        long minutes = ((Number) minutesSpinner.getValue()).longValue();
        long seconds = ((Number) secondsSpinner.getValue()).longValue();
        minutes = Math.max(0, minutes);
        seconds = Math.max(0, seconds);
        return (minutes * 60L + seconds) * 1000L;
    }

    private void startFrom(int player) {
        applyElapsedTime();
        if (remainingMilliseconds[player] <= 0) {
            return;
        }
        activePlayer = player;
        running = true;
        lastUpdateNanoseconds = System.nanoTime();
        render();
    }

    private void switchPlayer() {
        if (!running || activePlayer < 0) {
            return;
        }
        applyElapsedTime();
        if (remainingMilliseconds[activePlayer] <= 0) {
            remainingMilliseconds[activePlayer] = 0;
            running = false;
            render();
            return;
        }
        int nextPlayer = 1 - activePlayer;
        if (remainingMilliseconds[nextPlayer] <= 0) {
            running = false;
        } else {
            activePlayer = nextPlayer;
            lastUpdateNanoseconds = System.nanoTime();
        }
        render();
    }

    private void togglePause() {
        if (activePlayer < 0) {
            return;
        }
        if (running) {
            applyElapsedTime();
            running = false;
        } else {
            resumeTimer();
        }
        render();
    }

    private void resumeTimer() {
        if (activePlayer >= 0 && remainingMilliseconds[activePlayer] > 0) {
            running = true;
            lastUpdateNanoseconds = System.nanoTime();
            render();
        }
    }

    private void resetTimer() {
        durationMilliseconds = getConfiguredDuration();
        remainingMilliseconds[0] = durationMilliseconds;
        remainingMilliseconds[1] = durationMilliseconds;
        activePlayer = -1;
        running = false;
        lastUpdateNanoseconds = System.nanoTime();
        render();
    }

    private void applyElapsedTime() {
        if (!running || activePlayer < 0) {
            return;
        }
        long currentNanoseconds = System.nanoTime();
        double elapsedMilliseconds =
            (currentNanoseconds - lastUpdateNanoseconds) / 1_000_000.0;
        remainingMilliseconds[activePlayer] = Math.max(
            0,
            remainingMilliseconds[activePlayer] - elapsedMilliseconds
        );
        lastUpdateNanoseconds = currentNanoseconds;
    }

    private void updateTimer() {
        if (running && activePlayer >= 0) {
            applyElapsedTime();
            if (remainingMilliseconds[activePlayer] <= 0) {
                remainingMilliseconds[activePlayer] = 0;
                running = false;
            }
            render();
        } else {
            lastUpdateNanoseconds = System.nanoTime();
        }
    }

    private String formatTime(double milliseconds) {
        long totalMilliseconds = Math.max(0, (long) milliseconds);
        long totalSeconds = totalMilliseconds / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long tenths = (totalMilliseconds % 1000L) / 100L;
        return String.format("%02d:%02d.%d", minutes, seconds, tenths);
    }

    private void render() {
        for (int player = 0; player < 2; player++) {
            boolean isActive = running && activePlayer == player;
            playerCards[player].setActive(isActive);
            playerCards[player].setTime(formatTime(remainingMilliseconds[player]));
            if (remainingMilliseconds[player] <= 0) {
                playerCards[player].setState("TIME UP");
            } else if (isActive) {
                playerCards[player].setState("▶ START中");
            } else {
                playerCards[player].setState("待機中");
            }
        }
        if (running) {
            pauseButton.setText("一時停止");
        } else if (activePlayer >= 0) {
            pauseButton.setText("再開");
        } else {
            pauseButton.setText("一時停止");
        }
    }

    private void showNameDialog() {
        JTextField playerAField = new JTextField(playerCards[0].getPlayerName(), 15);
        JTextField playerBField = new JTextField(playerCards[1].getPlayerName(), 15);
        playerAField.setFont(UI_FONT);
        playerBField.setFont(UI_FONT);
        JPanel dialogPanel = new JPanel(new GridLayout(2, 2, 10, 12));
        dialogPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel playerALabel = new JLabel("プレイヤーA");
        JLabel playerBLabel = new JLabel("プレイヤーB");
        playerALabel.setFont(UI_FONT.deriveFont(Font.BOLD));
        playerBLabel.setFont(UI_FONT.deriveFont(Font.BOLD));
        dialogPanel.add(playerALabel);
        dialogPanel.add(playerAField);
        dialogPanel.add(playerBLabel);
        dialogPanel.add(playerBField);
        int result = JOptionPane.showConfirmDialog(
            this,
            dialogPanel,
            "名前設定",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String playerAName = playerAField.getText().trim();
        String playerBName = playerBField.getText().trim();
        playerCards[0].setPlayerName(playerAName.isEmpty() ? "A" : playerAName);
        playerCards[1].setPlayerName(playerBName.isEmpty() ? "B" : playerBName);
    }

    private void toggleFullscreen() {
        if (fullscreen) {
            leaveFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        if (fullscreen) {
            return;
        }
        normalBounds = getBounds();
        dispose();
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);
        fullscreen = true;
    }

    private void leaveFullscreen() {
        if (!fullscreen) {
            return;
        }
        dispose();
        setUndecorated(false);
        setExtendedState(JFrame.NORMAL);
        if (normalBounds != null) {
            setBounds(normalBounds);
        }
        setVisible(true);
        fullscreen = false;
    }

    private static final class PlayerCard extends JPanel {
        private static final int BORDER_WIDTH = 12;
        private static final int CORNER_RADIUS = 22;
        private final JLabel nameLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel timeLabel = new JLabel("02:00.0", SwingConstants.CENTER);
        private final JLabel stateLabel = new JLabel("待機中", SwingConstants.CENTER);
        private boolean active = false;
        private Runnable clickAction;

        private PlayerCard(String initialName) {
            setLayout(new GridBagLayout());
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            nameLabel.setText(initialName);
            nameLabel.setFont(NAME_FONT);
            timeLabel.setFont(TIME_FONT);
            stateLabel.setFont(STATE_FONT);
            nameLabel.setForeground(Color.WHITE);
            timeLabel.setForeground(Color.WHITE);
            stateLabel.setForeground(Color.WHITE);
            nameLabel.setFocusable(false);
            timeLabel.setFocusable(false);
            stateLabel.setFocusable(false);
            JPanel contentPanel = new JPanel();
            contentPanel.setOpaque(false);
            contentPanel.setFocusable(false);
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            stateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(nameLabel);
            contentPanel.add(Box.createVerticalStrut(15));
            contentPanel.add(timeLabel);
            contentPanel.add(Box.createVerticalStrut(15));
            contentPanel.add(stateLabel);
            add(contentPanel);
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (clickAction != null) {
                        clickAction.run();
                    }
                }
            };
            addMouseListener(mouseAdapter);
            contentPanel.addMouseListener(mouseAdapter);
            nameLabel.addMouseListener(mouseAdapter);
            timeLabel.addMouseListener(mouseAdapter);
            stateLabel.addMouseListener(mouseAdapter);
        }

        private void setClickAction(Runnable clickAction) {
            this.clickAction = clickAction;
        }

        private void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        private void setTime(String time) {
            timeLabel.setText(time);
        }

        private void setState(String state) {
            stateLabel.setText(state);
        }

        private String getPlayerName() {
            return nameLabel.getText();
        }

        private void setPlayerName(String playerName) {
            nameLabel.setText(playerName);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );
            int width = getWidth();
            int height = getHeight();
            Color borderColor = active ? ACTIVE_BORDER : WAIT_BORDER;
            Color backgroundColor = active ? ACTIVE_BG : WAIT_BG;
            graphics2D.setColor(borderColor);
            graphics2D.fillRoundRect(
                0,
                0,
                width,
                height,
                CORNER_RADIUS,
                CORNER_RADIUS
            );
            graphics2D.setColor(backgroundColor);
            graphics2D.fillRoundRect(
                BORDER_WIDTH,
                BORDER_WIDTH,
                Math.max(0, width - BORDER_WIDTH * 2),
                Math.max(0, height - BORDER_WIDTH * 2),
                Math.max(4, CORNER_RADIUS - BORDER_WIDTH),
                Math.max(4, CORNER_RADIUS - BORDER_WIDTH)
            );
            graphics2D.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedButton extends JButton {
        private static final int CORNER_RADIUS = 16;
        private final Color normalColor;
        private final Color hoverColor;
        private final Color pressedColor;
        private Shape hitShape;

        private RoundedButton(String text, Color background, Color foreground) {
            super(text);
            normalColor = background;
            hoverColor = changeBrightness(background, 18);
            pressedColor = changeBrightness(background, -18);
            setFont(BUTTON_FONT);
            setForeground(foreground);
            setBorder(new EmptyBorder(9, 16, 9, 16));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);

            /*
             * JButtonがSpaceキーをクリック操作として処理しないよう、
             * キーボードフォーカスの対象外にする。
             */
            setFocusable(false);
            setRequestFocusEnabled(false);

            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );
            ButtonModel model = getModel();
            Color fillColor;
            if (model.isPressed()) {
                fillColor = pressedColor;
            } else if (model.isRollover()) {
                fillColor = hoverColor;
            } else {
                fillColor = normalColor;
            }
            graphics2D.setColor(fillColor);
            graphics2D.fillRoundRect(
                0,
                0,
                getWidth(),
                getHeight(),
                CORNER_RADIUS,
                CORNER_RADIUS
            );
            graphics2D.dispose();
            super.paintComponent(graphics);
        }

        @Override
        public boolean contains(int x, int y) {
            if (
                hitShape == null
                || hitShape.getBounds().width != getWidth()
                || hitShape.getBounds().height != getHeight()
            ) {
                hitShape = new RoundRectangle2D.Float(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    CORNER_RADIUS,
                    CORNER_RADIUS
                );
            }
            return hitShape.contains(x, y);
        }

        private static Color changeBrightness(Color color, int amount) {
            return new Color(
                Math.max(0, Math.min(255, color.getRed() + amount)),
                Math.max(0, Math.min(255, color.getGreen() + amount)),
                Math.max(0, Math.min(255, color.getBlue() + amount))
            );
        }
    }

    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            TaisenTimer timer = new TaisenTimer();
            timer.setVisible(true);
        });
    }
}