import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 產生圖文選單圖片（2500x1686），區塊需與 richmenu.json 的 bounds 一致。
 * 用法：java RichMenuImage.java [輸出檔名，預設 richmenu.png]
 */
public class RichMenuImage {

    static final int WIDTH = 2500;
    static final int HEIGHT = 1686;
    static final int HALF = 843;
    static final int GAP = 18;

    static final Color BACKGROUND = new Color(0xEEF3F6);
    static final Color PRIMARY = new Color(0x2E86C1);
    static final Color PRIMARY_DARK = new Color(0x1F618D);
    static final Color CARD = Color.WHITE;
    static final Color TEXT = new Color(0x2C3E50);
    static final Color SUBTLE = new Color(0x7F8C8D);

    public static void main(String[] args) throws Exception {
        String fontName = pickFont();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        drawTrackButton(g, fontName);

        List<String> labels = List.of("診間", "目前狀態", "取消追蹤", "幫助");
        int cellWidth = WIDTH / labels.size();
        for (int i = 0; i < labels.size(); i++) {
            drawSmallButton(g, fontName, i * cellWidth, HALF, cellWidth, HALF, labels.get(i), i);
        }
        g.dispose();

        File output = new File(args.length > 0 ? args[0] : "richmenu.png");
        ImageIO.write(image, "png", output);
        System.out.printf("Wrote %s (%d KB) using font: %s%n", output, output.length() / 1024, fontName);
    }

    static void drawTrackButton(Graphics2D g, String fontName) {
        int x = GAP;
        int y = GAP;
        int w = WIDTH - GAP * 2;
        int h = HALF - GAP * 3 / 2;
        g.setPaint(new GradientPaint(x, y, PRIMARY, x + w, y + h, PRIMARY_DARK));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 80, 80));

        // 鈴鐺圖示
        double cx = 520;
        double cy = y + h / 2.0;
        g.setColor(Color.WHITE);
        drawBell(g, cx, cy, 300);

        int textX = 860;
        g.setFont(new Font(fontName, Font.BOLD, 190));
        g.drawString("追蹤看診號碼", textX, (int) cy + 10);
        g.setColor(new Color(255, 255, 255, 220));
        g.setFont(new Font(fontName, Font.PLAIN, 80));
        g.drawString("選擇診間 → 輸入號碼，快輪到時通知您", textX, (int) cy + 150);
    }

    static void drawSmallButton(Graphics2D g, String fontName, int cellX, int cellY, int cellW, int cellH,
            String label, int iconIndex) {
        int x = cellX + (iconIndex == 0 ? GAP : GAP / 2);
        int w = cellW - (iconIndex == 0 || iconIndex == 3 ? GAP * 3 / 2 : GAP);
        int y = cellY + GAP / 2;
        int h = cellH - GAP * 3 / 2;
        g.setColor(CARD);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 70, 70));

        double cx = x + w / 2.0;
        double cy = y + h * 0.40;
        g.setColor(PRIMARY);
        g.setStroke(new BasicStroke(26, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (iconIndex) {
            case 0 -> drawRooms(g, cx, cy);
            case 1 -> drawClock(g, cx, cy);
            case 2 -> drawCancel(g, cx, cy);
            default -> drawHelp(g, cx, cy, fontName);
        }

        g.setColor(TEXT);
        g.setFont(new Font(fontName, Font.BOLD, 104));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(label, (int) (cx - metrics.stringWidth(label) / 2.0), (int) (y + h * 0.82));
    }

    static void drawBell(Graphics2D g, double cx, double cy, double size) {
        double w = size * 0.78;
        double top = cy - size * 0.42;
        double bottom = cy + size * 0.28;
        Path2D bell = new Path2D.Double();
        bell.moveTo(cx - w / 2, bottom);
        bell.curveTo(cx - w * 0.38, bottom - size * 0.12, cx - w * 0.42, top + size * 0.10, cx - w * 0.30, top + size * 0.02);
        bell.curveTo(cx - w * 0.18, top - size * 0.06, cx + w * 0.18, top - size * 0.06, cx + w * 0.30, top + size * 0.02);
        bell.curveTo(cx + w * 0.42, top + size * 0.10, cx + w * 0.38, bottom - size * 0.12, cx + w / 2, bottom);
        bell.closePath();
        g.fill(bell);
        g.fill(new RoundRectangle2D.Double(cx - w * 0.58, bottom - size * 0.02, w * 1.16, size * 0.09, 30, 30));
        g.fill(new Ellipse2D.Double(cx - size * 0.11, bottom + size * 0.08, size * 0.22, size * 0.16));
        g.fill(new Ellipse2D.Double(cx - size * 0.06, top - size * 0.14, size * 0.12, size * 0.12));
    }

    static void drawRooms(Graphics2D g, double cx, double cy) {
        double s = 64;
        double step = s + 50;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                double x = cx - (s + step) / 2 + col * step;
                double y = cy - (s + step) / 2 + row * step;
                g.draw(new RoundRectangle2D.Double(x, y, s, s, 24, 24));
            }
        }
    }

    static void drawClock(Graphics2D g, double cx, double cy) {
        double r = 105;
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.draw(new Line2D.Double(cx, cy, cx, cy - r * 0.6));
        g.draw(new Line2D.Double(cx, cy, cx + r * 0.45, cy + r * 0.25));
    }

    static void drawCancel(Graphics2D g, double cx, double cy) {
        double r = 105;
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        double d = r * 0.42;
        g.draw(new Line2D.Double(cx - d, cy - d, cx + d, cy + d));
        g.draw(new Line2D.Double(cx + d, cy - d, cx - d, cy + d));
    }

    static void drawHelp(Graphics2D g, double cx, double cy, String fontName) {
        double r = 105;
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.draw(new Arc2D.Double(cx - 42, cy - 62, 84, 74, 160, -230, Arc2D.OPEN));
        g.draw(new Line2D.Double(cx + 10, cy + 8, cx, cy + 22));
        g.fill(new Ellipse2D.Double(cx - 15, cy + 48, 30, 30));
    }

    static String pickFont() {
        List<String> candidates = List.of("PingFang TC", "Heiti TC", "Hiragino Sans GB", "STHeiti",
                "Noto Sans CJK TC", "Noto Sans TC", "Microsoft JhengHei");
        List<String> available = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String candidate : candidates) {
            if (available.contains(candidate) && new Font(candidate, Font.PLAIN, 12).canDisplayUpTo("追蹤看診號碼") == -1) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到可顯示中文的字型，請安裝 Noto Sans CJK TC");
    }

}
