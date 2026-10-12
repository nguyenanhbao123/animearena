package com.animeclash;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class CreateHitSprites {
    public static void main(String[] args) {
        String[] chars = {"naru", "gokai", "kira", "zin"};
        String resDir = "res/images/";

        for (String c : chars) {
            try {
                File inFile = new File(resDir + c + "_idle.png");
                if (!inFile.exists() && c.equals("naru")) {
                    inFile = new File(resDir + "naru.png");
                }
                if (!inFile.exists()) {
                    System.out.println("Could not find base image for " + c);
                    continue;
                }

                BufferedImage base = ImageIO.read(inFile);
                int w = base.getWidth();
                int h = base.getHeight();

                // Tạo ảnh mới cùng kích thước và hệ màu ARGB
                BufferedImage hitImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

                // Quét từng pixel, giữ nguyên Alpha, tăng kênh Đỏ (Red) và blend nhẹ màu chấn động
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = base.getRGB(x, y);
                        int alpha = (argb >> 24) & 0xFF;
                        if (alpha == 0) {
                            hitImg.setRGB(x, y, argb);
                            continue;
                        }

                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;

                        // Tăng sắc đỏ (Red tint) và độ sáng tạo cảm giác bị trúng đòn chấn động
                        r = Math.min(255, (int)(r * 1.4 + 50));
                        g = (int)(g * 0.85);
                        b = (int)(b * 0.85);

                        int newArgb = (alpha << 24) | (r << 16) | (g << 8) | b;
                        hitImg.setRGB(x, y, newArgb);
                    }
                }

                File outFile = new File(resDir + c + "_hit.png");
                ImageIO.write(hitImg, "PNG", outFile);
                System.out.println("Created perfectly matching hit sprite: " + outFile.getPath());

            } catch (Exception e) {
                System.err.println("Error creating hit sprite for " + c + ": " + e.getMessage());
            }
        }
    }
}
