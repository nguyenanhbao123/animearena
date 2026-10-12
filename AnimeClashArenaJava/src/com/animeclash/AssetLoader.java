package com.animeclash;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * AssetLoader — Tải ảnh nhân vật và nền vào game
 *
 * ★ Chiến lược load (portable — chạy được trên mọi máy):
 *   1. Classpath resource: /images/xxx.png (khi đóng JAR)
 *   2. File system: res/images/xxx.png (khi chạy trong IDE)
 *   3. Fallback: null → game vẽ bằng đồ họa hình học
 *
 * ★ makeTransparent() cải tiến: chỉ xóa trắng thuần + checkerboard,
 *   giữ nguyên pixel sáng hợp lệ (áo, vũ khí, phản chiếu)
 */
public class AssetLoader {

    // ─── Background ───────────────────────────────────
    public static BufferedImage bgImage;

    // ─── NARU sprites ─────────────────────────────────
    public static BufferedImage naruIdle;
    public static BufferedImage naruAttack;
    public static BufferedImage naruSkill1;
    public static BufferedImage naruSkill2;
    public static BufferedImage naruUltimate;

    // ─── GOKAI sprites ────────────────────────────────
    public static BufferedImage gokaiIdle;
    public static BufferedImage gokaiAttack;
    public static BufferedImage gokaiSkill1;
    public static BufferedImage gokaiSkill2;
    public static BufferedImage gokaiUltimate;

    // ─── KIRA sprites ─────────────────────────────────
    public static BufferedImage kiraIdle;
    public static BufferedImage kiraAttack;
    public static BufferedImage kiraSkill1;
    public static BufferedImage kiraSkill2;
    public static BufferedImage kiraUltimate;

    // ─── ZIN sprites ──────────────────────────────────
    public static BufferedImage zinIdle;
    public static BufferedImage zinAttack;
    public static BufferedImage zinSkill1;
    public static BufferedImage zinSkill2;
    public static BufferedImage zinUltimate;

    // ─── HIT sprites (Trạng thái trúng chiêu) ─────────
    public static BufferedImage naruHit;
    public static BufferedImage gokaiHit;
    public static BufferedImage kiraHit;
    public static BufferedImage zinHit;

    // Đường dẫn resource (relative từ project root hoặc classpath)
    private static final String RES_DIR = "res/images/";

    static {
        try {
            bgImage      = loadImage("bg.png");
            naruIdle     = loadImage("naru_idle.png");
            if (naruIdle == null) naruIdle = loadImage("naru.png");
            naruAttack   = loadImage("naru_attack.png");
            gokaiIdle    = loadImage("gokai_idle.png");
            gokaiAttack  = loadImage("gokai_attack.png");
            
            // Lấy ảnh skill (sẽ null nếu file chưa có)
            naruSkill1   = loadImage("naru_skill1.png");
            naruSkill2   = loadImage("naru_skill2.png");
            naruUltimate = loadImage("naru_ultimate.png");
            
            gokaiSkill1  = loadImage("gokai_skill1.png");
            gokaiSkill2  = loadImage("gokai_skill2.png");
            gokaiUltimate= loadImage("gokai_ultimate.png");
            
            kiraIdle     = loadImage("kira_idle.png");
            kiraAttack   = loadImage("kira_attack.png");
            kiraSkill1   = loadImage("kira_skill1.png");
            kiraSkill2   = loadImage("kira_skill2.png");
            kiraUltimate = loadImage("kira_ultimate.png");
            
            zinIdle      = loadImage("zin_idle.png");
            zinAttack    = loadImage("zin_attack.png");
            zinSkill1    = loadImage("zin_skill1.png");
            zinSkill2    = loadImage("zin_skill2.png");
            zinUltimate  = loadImage("zin_ultimate.png");

            // Tự động kiểm tra và tạo 4 file ảnh naru_hit.png, gokai_hit.png, kira_hit.png, zin_hit.png từ ảnh gốc
            CreateHitSprites.main(null);

            naruHit      = loadImage("naru_hit.png");
            gokaiHit     = loadImage("gokai_hit.png");
            kiraHit      = loadImage("kira_hit.png");
            zinHit       = loadImage("zin_hit.png");

            System.out.println(String.format(
                "[AssetLoader] Loaded OK — bg:%b naru:%b gokai:%b kira:%b zin:%b",
                bgImage != null, naruIdle != null, gokaiIdle != null, kiraIdle != null, zinIdle != null
            ));

            // Xóa nền checkerboard / trắng (cải tiến: ít aggressive hơn)
            naruIdle     = makeTransparent(naruIdle);
            naruAttack   = makeTransparent(naruAttack);
            gokaiIdle    = makeTransparent(gokaiIdle);
            gokaiAttack  = makeTransparent(gokaiAttack);
            
            if (naruSkill1 != null) naruSkill1 = makeTransparent(naruSkill1);
            if (naruSkill2 != null) naruSkill2 = makeTransparent(naruSkill2);
            if (naruUltimate != null) naruUltimate = makeTransparent(naruUltimate);
            
            if (gokaiSkill1 != null) gokaiSkill1 = makeTransparent(gokaiSkill1);
            if (gokaiSkill2 != null) gokaiSkill2 = makeTransparent(gokaiSkill2);
            if (gokaiUltimate != null) gokaiUltimate = makeTransparent(gokaiUltimate);
            
            if (kiraIdle != null) kiraIdle = makeTransparent(kiraIdle);
            if (kiraAttack != null) kiraAttack = makeTransparent(kiraAttack);
            if (kiraSkill1 != null) kiraSkill1 = makeTransparent(kiraSkill1);
            if (kiraSkill2 != null) kiraSkill2 = makeTransparent(kiraSkill2);
            if (kiraUltimate != null) kiraUltimate = makeTransparent(kiraUltimate);

            if (zinIdle != null) zinIdle = makeTransparent(zinIdle);
            if (zinAttack != null) zinAttack = makeTransparent(zinAttack);
            if (zinSkill1 != null) zinSkill1 = makeTransparent(zinSkill1);
            if (zinSkill2 != null) zinSkill2 = makeTransparent(zinSkill2);
            if (zinUltimate != null) zinUltimate = makeTransparent(zinUltimate);

            if (naruHit != null) naruHit = makeTransparent(naruHit);
            if (gokaiHit != null) gokaiHit = makeTransparent(gokaiHit);
            if (kiraHit != null) kiraHit = makeTransparent(kiraHit);
            if (zinHit != null) zinHit = makeTransparent(zinHit);

            System.out.println("[AssetLoader] Loaded OK"
                + " — bg:" + (bgImage != null)
                + " naru:" + (naruIdle != null)
                + " gokai:" + (gokaiIdle != null));

        } catch (Exception e) {
            System.err.println("[AssetLoader] Error: " + e.getMessage());
        }
    }

    /** Gọi để kích hoạt static block */
    public static void init() {}

    // ─── Load image: thử classpath trước, rồi file system ──
    private static BufferedImage loadImage(String filename) {
        BufferedImage img = null;

        // 1. Thử classpath resource (cho JAR deployment)
        try {
            InputStream is = AssetLoader.class.getResourceAsStream("/images/" + filename);
            if (is != null) {
                img = ImageIO.read(is);
                is.close();
            }
        } catch (Exception e) {
            // ignore, try next
        }

        // 2. Thử file system relative path (cho IDE)
        if (img == null) {
            try {
                File f = new File(RES_DIR + filename);
                if (f.exists()) {
                    img = ImageIO.read(f);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 3. Thử thêm vài path backup
        if (img == null) {
            String[] backupPaths = {
                "images/" + filename,
                "../res/images/" + filename,
                "src/resources/images/" + filename
            };
            for (String path : backupPaths) {
                try {
                    File f = new File(path);
                    if (f.exists()) {
                        img = ImageIO.read(f);
                        break;
                    }
                } catch (Exception e) { /* ignore */ }
            }
        }

        if (img == null) {
            System.err.println("[AssetLoader] Không tìm thấy: " + filename + " → dùng fallback đồ họa");
            return null;
        }

        // Chuyển về ARGB để hỗ trợ alpha
        BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(img, 0, 0, null);
        return argb;
    }

    /**
     * ★ Xóa nền checkerboard / trắng — phiên bản CẢI TIẾN
     * 
     * Chỉ xóa:
     * - Pixel trắng thuần (r>240, g>240, b>240)
     * - Pixel thuộc checkerboard pattern (xám nhạt xen kẽ trắng)
     * - Pixel khớp màu nền 4 góc
     * 
     * KHÔNG xóa:
     * - Pixel sáng có màu rõ (áo sáng, vũ khí, phản chiếu)
     * - Pixel xám có saturation > 0 (không phải grayscale thuần)
     */
    private static BufferedImage makeTransparent(BufferedImage img) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();

        // Lấy mẫu màu nền từ 4 góc
        int[] bgSamples = {
            img.getRGB(0, 0), img.getRGB(w-1, 0),
            img.getRGB(0, h-1), img.getRGB(w-1, h-1)
        };

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = img.getRGB(x, y);
                int a  = (px >> 24) & 0xFF;
                int r  = (px >> 16) & 0xFF;
                int g  = (px >> 8)  & 0xFF;
                int b  = px & 0xFF;

                // 1. Pixel đã có alpha thấp → giữ trong suốt
                if (a < 30) { out.setRGB(x, y, 0x00000000); continue; }

                // 2. Xóa trắng thuần (r>240 AND g>240 AND b>240)
                if (r > 240 && g > 240 && b > 240) {
                    out.setRGB(x, y, 0x00000000); continue;
                }

                // 3. Checkerboard detection: xám thuần (grayscale) VÀ rất sáng
                //    Chỉ xóa nếu r,g,b gần nhau (grayscale) VÀ > 200
                boolean isGrayscale = Math.abs(r-g) < 10 && Math.abs(g-b) < 10;
                if (isGrayscale && r > 200) {
                    out.setRGB(x, y, 0x00000000); continue;
                }

                // 4. Kiểm tra gần màu nền 4 góc
                boolean nearBg = false;
                for (int bg : bgSamples) {
                    int bgR = (bg >> 16) & 0xFF, bgG = (bg >> 8) & 0xFF, bgB = bg & 0xFF;
                    double dist = colorDist(px, bg);
                    // Nếu màu nền là đen thuần (bgR < 20), dùng threshold nhỏ (15) để không ăn vào viền nhân vật
                    double threshold = (bgR < 20 && bgG < 20 && bgB < 20) ? 15 : 25;
                    if (dist < threshold) { nearBg = true; break; }
                }
                if (nearBg) { out.setRGB(x, y, 0x00000000); continue; }

                out.setRGB(x, y, px);
            }
        }
        return out;
    }

    private static double colorDist(int c1, int c2) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return Math.sqrt((r1-r2)*(r1-r2) + (g1-g2)*(g1-g2) + (b1-b2)*(b1-b2));
    }
}
