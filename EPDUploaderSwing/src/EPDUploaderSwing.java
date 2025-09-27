import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Function;

public class EPDUploaderSwing extends JFrame {
    private JLabel previewLabel;
    private BufferedImage imgOrig = null;
    private JComboBox<String> angleCombo;
    private JSlider contrastSlider, ditherSlider, brightnessSlider, saturationSlider, sharpenSlider;
    private byte[] bwBytes, rdBytes;
    private JTextField esp32AddressField;
    private JPanel left;

    public EPDUploaderSwing() {
        super("墨水屏相册上传工具");
        setSize(900, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 左侧面板
        left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnOpen = new JButton("打开图片");
        btnOpen.addActionListener(e -> openImage());
        left.add(btnOpen);
        left.add(Box.createVerticalStrut(10));

        left.add(new JLabel("设备地址"));
        esp32AddressField = new JTextField("http://192.168.3.7/upload");
        left.add(esp32AddressField);
        left.add(Box.createVerticalStrut(10));

        left.add(new JLabel("旋转角度"));
        angleCombo = new JComboBox<>(new String[]{"0°", "90°", "180°", "270°"});
        angleCombo.addActionListener(e -> updatePreview());
        left.add(angleCombo);
        left.add(Box.createVerticalStrut(10));

        // 调节项
        brightnessSlider = makeSliderWithLabel("亮度", -100, 100, 0);
        contrastSlider = makeSliderWithLabel("对比度", -100, 100, 0);
        saturationSlider = makeSliderWithLabel("饱和度", -100, 100, 0);
        sharpenSlider = makeSliderWithLabel("锐化", 0, 200, 0);
        ditherSlider = makeSliderWithLabel("抖动强度", 0, 100, 100);

        JButton btnUpload = new JButton("上传到设备");
        btnUpload.addActionListener(e -> upload());
        left.add(btnUpload);

        add(left, BorderLayout.WEST);

        // 预览区
        previewLabel = new JLabel();
        previewLabel.setPreferredSize(new Dimension(800, 600));
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        previewLabel.setHorizontalAlignment(JLabel.CENTER);
        add(previewLabel, BorderLayout.CENTER);
    }

    private JSlider makeSliderWithLabel(String name, int min, int max, int val) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(name);
        JLabel valueLabel = new JLabel(String.valueOf(val), JLabel.RIGHT);
        JSlider slider = new JSlider(min, max, val);
        slider.addChangeListener(e -> {
            valueLabel.setText(String.valueOf(slider.getValue()));
            updatePreview();
        });
        panel.add(label, BorderLayout.WEST);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(valueLabel, BorderLayout.EAST);
        left.add(panel);
        left.add(Box.createVerticalStrut(10));
        return slider;
    }

    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                imgOrig = ImageIO.read(chooser.getSelectedFile());
                updatePreview();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "读取图片失败: " + ex.getMessage());
            }
        }
    }

    private void updatePreview() {
        if (imgOrig == null) return;

        BufferedImage img = imgOrig;
        int angle = angleCombo.getSelectedIndex() * 90;
        img = rotate(img, angle);

        BufferedImage fitted = fitWithPadding(img, 400, 300);

        int w = fitted.getWidth();
        int h = fitted.getHeight();

        // 调整图像
        BufferedImage adjusted = applyAdjustments(fitted,
                brightnessSlider.getValue(),
                contrastSlider.getValue(),
                saturationSlider.getValue(),
                sharpenSlider.getValue());

        int[] pixels = adjusted.getRGB(0, 0, w, h, null, 0, w);

        double strength = ditherSlider.getValue() / 100.0;

        // 黑白层
        int[] bw = floydSteinbergDither(pixels, w, h, strength,
                rgb -> (0.299 * ((rgb >> 16) & 0xff)
                        + 0.587 * ((rgb >> 8) & 0xff)
                        + 0.114 * (rgb & 0xff)));

        // 红色层（简单差值法）
        int[] rd = floydSteinbergDither(pixels, w, h, strength,
                rgb -> {
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    double intensity = r - 0.5 * (g + b);
                    if (intensity < 0) intensity = 0;
                    return intensity;
                });

        // 反相红色
        for (int i = 0; i < w * h; i++) rd[i] = 1 - rd[i];

        // 合成预览图
        int[] preview = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            if (bw[i] == 1 && rd[i] == 0) preview[i] = 0xff000000; // 黑
            else if (rd[i] == 1) preview[i] = 0xffff0000;           // 红
            else preview[i] = 0xffffffff;                          // 白
        }

        bwBytes = packBits(bw);
        rdBytes = packBits(rd);

        BufferedImage imgPreview = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        imgPreview.setRGB(0, 0, w, h, preview, 0, w);

        // 放大 1.5 倍
        int newW = (int) (w * 1.5);
        int newH = (int) (h * 1.5);
        Image scaled = imgPreview.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);

        // 创建画布居中显示
        BufferedImage canvas = new BufferedImage(previewLabel.getWidth(), previewLabel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = canvas.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int offsetX = (canvas.getWidth() - newW) / 2;
        int offsetY = (canvas.getHeight() - newH) / 2;
        g2d.drawImage(scaled, offsetX, offsetY, null);
        g2d.dispose();

        previewLabel.setIcon(new ImageIcon(canvas));
    }

    private BufferedImage applyAdjustments(BufferedImage src, int brightness, int contrast, int saturation, int sharpen) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        double contrastFactor = (259.0 * (contrast + 255)) / (255.0 * (259 - contrast));
        double satFactor = (saturation + 100) / 100.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                // 亮度
                r = truncate(r + brightness);
                g = truncate(g + brightness);
                b = truncate(b + brightness);

                // 对比度
                r = truncate(contrastFactor * (r - 128) + 128);
                g = truncate(contrastFactor * (g - 128) + 128);
                b = truncate(contrastFactor * (b - 128) + 128);

                // 饱和度
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                hsb[1] = Math.min(1.0f, (float) (hsb[1] * satFactor));
                int newRGB = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);

                out.setRGB(x, y, newRGB & 0xffffff);
            }
        }

        if (sharpen > 0) {
            out = applySharpen(out, sharpen / 100.0);
        }

        return out;
    }

    private BufferedImage applySharpen(BufferedImage src, double amount) {
        float[] kernel = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] *= amount;
        }
        Kernel k = new Kernel(3, 3, kernel);
        ConvolveOp op = new ConvolveOp(k, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }

    private int truncate(double val) {
        return (int) Math.max(0, Math.min(255, val));
    }

    private int[] floydSteinbergDither(int[] pixels, int w, int h, double strength,
                                       Function<Integer, Double> intensityFunc) {
        double[][] val = new double[h][w];
        int[] out = new int[w * h];

        double maxVal = 1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = intensityFunc.apply(pixels[y * w + x]);
                val[y][x] = v;
                if (v > maxVal) maxVal = v;
            }

        double scale = 255.0 / maxVal;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                val[y][x] *= scale;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int oldVal = (int) val[y][x];
                int newVal = (oldVal > 128 ? 255 : 0);
                out[y * w + x] = (newVal == 0 ? 1 : 0);
                int err = oldVal - newVal;

                if (x + 1 < w) val[y][x + 1] += err * 7 / 16.0 * strength;
                if (y + 1 < h) {
                    if (x > 0) val[y + 1][x - 1] += err * 3 / 16.0 * strength;
                    val[y + 1][x] += err * 5 / 16.0 * strength;
                    if (x + 1 < w) val[y + 1][x + 1] += err * 1 / 16.0 * strength;
                }
            }
        }
        return out;
    }

    private byte[] packBits(int[] bits) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int cur = 0, count = 0;
        for (int b : bits) {
            cur = (cur << 1) | (b & 1);
            if (++count == 8) {
                out.write(cur);
                cur = 0;
                count = 0;
            }
        }
        if (count > 0) out.write(cur << (8 - count));
        return out.toByteArray();
    }

    private BufferedImage rotate(BufferedImage src, int angle) {
        if (angle == 0) return src;
        double rads = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(rads)), cos = Math.abs(Math.cos(rads));
        int w = src.getWidth(), h = src.getHeight();
        int newW = (int) (w * cos + h * sin);
        int newH = (int) (h * cos + w * sin);

        BufferedImage rotated = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newW, newH);
        g2d.translate((newW - w) / 2, (newH - h) / 2);
        g2d.rotate(rads, w / 2.0, h / 2.0);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    private BufferedImage fitWithPadding(BufferedImage img, int targetW, int targetH) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();

        double scale = Math.min((double) targetW / imgW, (double) targetH / imgH);
        int newW = (int) Math.round(imgW * scale);
        int newH = (int) Math.round(imgH * scale);

        BufferedImage scaledImg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaledImg.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newW, newH);
        g2d.drawImage(img, 0, 0, newW, newH, null);
        g2d.dispose();

        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        g2d = out.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, targetW, targetH);

        int offsetX = (targetW - newW) / 2;
        int offsetY = (targetH - newH) / 2;
        g2d.drawImage(scaledImg, offsetX, offsetY, null);
        g2d.dispose();
        return out;
    }

    private void upload() {
        if (bwBytes == null || rdBytes == null) {
            JOptionPane.showMessageDialog(this, "请先打开并处理图片");
            return;
        }
        try {
            String url = esp32AddressField.getText().trim();
            postFile(url, "bw_image", bwBytes);
            postFile(url, "red_image", rdBytes);
            JOptionPane.showMessageDialog(this, "上传完成！");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "上传失败: " + e.getMessage());
        }
    }

    private void postFile(String urlStr, String field, byte[] data) throws IOException {
        String boundary = "----JavaSwingBoundary";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"" + field +
                    "\"; filename=\"" + field + ".bin\"\r\n");
            out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            out.write(data);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        if (conn.getResponseCode() != 200)
            throw new IOException("HTTP 错误码 " + conn.getResponseCode());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EPDUploaderSwing().setVisible(true));
    }
}
