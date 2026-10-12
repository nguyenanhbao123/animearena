package com.animeclash;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Toolkit;

/**
 * ANIME CLASH ARENA: THE GLITCHED TOURNAMENT
 * Entry Point — chạy trực tiếp trên Eclipse IDE
 * 
 * Cách chạy:
 *   1. Tạo Java Project mới trong Eclipse
 *   2. Tạo package: com.animeclash
 *   3. Copy tất cả file .java vào package
 *   4. Run as Java Application → AnimeClashArena.java
 */
public class AnimeClashArena {

    public static final int GAME_W = 960;
    public static final int GAME_H = 540;
    public static final String TITLE = "ANIME CLASH ARENA: The Glitched Tournament";

    public static void main(String[] args) {
        // Khởi tạo Asset (Copy ảnh từ cache và load vào bộ nhớ)
        AssetLoader.init();
        // Khởi tạo âm thanh
        SoundManager.init();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel gamePanel = new GamePanel();
            gamePanel.setPreferredSize(new Dimension(GAME_W, GAME_H));
            frame.add(gamePanel);

            frame.pack();

            // Căn giữa màn hình
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setLocation(
                (screen.width  - frame.getWidth())  / 2,
                (screen.height - frame.getHeight()) / 2
            );

            frame.setVisible(true);
            gamePanel.startGame();
        });
    }
}
