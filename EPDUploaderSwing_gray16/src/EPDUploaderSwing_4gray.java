import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class EPDUploaderSwing extends JFrame {
    private JLabel previewLabel;
    private BufferedImage imgOrig = null;
    private JComboBox<String> angleCombo;
    private JSlider contrastSlider, brightnessSlider, saturationSlider, sharpenSlider;
    private byte[] grayBytes;
    private JTextField esp32AddressField;
    private JPanel left;

    public EPDUploaderSwing() {
        super("墨水屏灰阶上传工具");
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

        // 固定输出为 400x300，保持比例并填充白边
        BufferedImage fitted = fitWithPadding(img, 400, 300);

        int w = fitted.getWidth();
        int h = fitted.getHeight();

        // === 垂直扫描 + 4灰阶（0~3）打包 ===
        grayBytes = new byte[(w * h + 3) / 4]; // 每字节存 4 个像素
        int[] grayLevels = new int[w * h];

        int idxByte = 0;
        int bitCount = 0;
        byte currentByte = 0;

        for (int x = 0; x < w; x++) { // 列优先（垂直扫描）
            for (int y = 0; y < h; y++) {
                int rgb = fitted.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                int gray4 = gray / 64; // 4灰阶 (0~3)
                grayLevels[x * h + y] = gray4; // 列优先存储

                // 每字节打包 4 个像素
                currentByte <<= 2;
                currentByte |= gray4 & 0x03;
                bitCount++;

                if (bitCount == 4) {
                    grayBytes[idxByte++] = currentByte;
                    currentByte = 0;
                    bitCount = 0;
                }
            }
        }
        // 补齐最后不足4个像素
        if (bitCount > 0) {
            currentByte <<= (2 * (4 - bitCount));
            grayBytes[idxByte++] = currentByte;
        }

        // === 生成预览图（垂直扫描映射） ===
        BufferedImage previewImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int gray4 = grayLevels[x * h + y];
                int g = gray4 * 85; // 映射到0,85,170,255
                int color = new Color(g, g, g).getRGB();
                previewImg.setRGB(x, y, color);
            }
        }

        Image scaled = previewImg.getScaledInstance(previewLabel.getWidth(), previewLabel.getHeight(), Image.SCALE_SMOOTH);
        previewLabel.setIcon(new ImageIcon(scaled));
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
        if (grayBytes == null) {
            JOptionPane.showMessageDialog(this, "请先打开并处理图片");
            return;
        }
        try {
            String url = esp32AddressField.getText().trim();
            postFile(url, "gray16", grayBytes);
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
