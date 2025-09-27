#include <SPIFFS.h>
#include <WiFi.h>
#include <WebServer.h>
#include <GxEPD2_3C.h>
#include <Fonts/FreeMonoBold12pt7b.h>
/*
#define BUSY_Pin  0
#define RES_Pin   6
#define DC_Pin    1
#define CS_Pin    7
#define SCK_Pin   2
#define SDI_Pin   3
*/
#define BUSY_Pin  8
#define RES_Pin   6
#define DC_Pin    9
#define CS_Pin    7
//#define SCK_Pin   2
//#define SDI_Pin   3
/*
找到
Arduino15/packages/esp32/hardware/esp32/<版本号>/variants/esp32c3/pins_arduino.h
将SPI管脚定义修改成下面这样
static const uint8_t SS    = 7;
static const uint8_t MOSI  = 3;
static const uint8_t MISO  = 10;
static const uint8_t SCK   = 2;
*/
#define EPD_W  400
#define EPD_H  300
#define BUF_SIZE  (EPD_W * EPD_H / 8)   // 15000 字节


GxEPD2_3C<GxEPD2_420c_Z21, EPD_W> display(GxEPD2_420c_Z21(CS_Pin, DC_Pin, RES_Pin, BUSY_Pin));
//GxEPD2_3C<GxEPD2_420c_E042A13, EPD_W> display(GxEPD2_420c_E042A13(CS_Pin, DC_Pin, RES_Pin, BUSY_Pin));
const char *ssid     = "EDP-PhotoAlbum";
const char *password = "12345678";

IPAddress local_IP(192, 168, 3, 7);//初始化时墨水屏显示的IP地址，需要在showAPInfo函数中配置
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);

WebServer server(80);
File uploadFile;

bool bwReady  = false;
bool redReady = false;

/* ---------- 上传回调 ---------- */
void handleUpload() {
  if (server.uri() != "/upload") return;
  HTTPUpload& up = server.upload();

  if (up.status == UPLOAD_FILE_START) {
    String filename = up.filename;
    if (!filename.startsWith("/")) filename = "/" + filename;

    Serial.print("Upload Start: "); Serial.println(filename);

    if (filename.indexOf("bw") >= 0)
      uploadFile = SPIFFS.open("/bw_image.bin", FILE_WRITE);
    else if (filename.indexOf("red") >= 0)
      uploadFile = SPIFFS.open("/red_image.bin", FILE_WRITE);

    if (!uploadFile) Serial.println("Failed to open file for writing");

  } else if (up.status == UPLOAD_FILE_WRITE) {
    if (uploadFile) uploadFile.write(up.buf, up.currentSize);

  } else if (up.status == UPLOAD_FILE_END) {
    if (uploadFile) uploadFile.close();
    Serial.println("Upload End");

    if (up.filename.indexOf("bw") >= 0)  bwReady  = true;
    if (up.filename.indexOf("red") >= 0) redReady = true;

    server.send(200, "text/plain", "Upload OK");

    if (bwReady && redReady) {          // 两层都到齐
      bwReady = redReady = false;       // 准备下次
      renderImageFromFlash("/bw_image.bin", "/red_image.bin");
    }
  }
}
/* ---------- 新增：显示 AP 信息 ---------- */
void showAPInfo() {
  display.firstPage();
  do {
    display.fillScreen(GxEPD_WHITE);

    // 选用合适字号，这里用 16 像素高的字体
    display.setFont(&FreeMonoBold12pt7b);
    display.setTextColor(GxEPD_BLACK);
    display.setTextWrap(false);

    String s1 = "WiFi: " + String(ssid);
    String s2 = "PWD:  " + String(password);
    const char *txt1 = s1.c_str();
    const char *txt2 = s2.c_str();
    const char *txt3 = "IP:   192.168.3.7";

    int16_t tbx, tby; uint16_t tbw, tbh;

    // 1. 整体居中计算
    display.getTextBounds(txt1, 0, 0, &tbx, &tby, &tbw, &tbh);
    int y = (EPD_H - 3 * tbh - 10) / 2 - tby;   // 3 行 + 10 像素间隙
    int x = (EPD_W - tbw) / 2 - tbx;

    display.setCursor(x, y);
    display.print(txt1);

    display.getTextBounds(txt2, 0, 0, &tbx, &tby, &tbw, &tbh);
    x = (EPD_W - tbw) / 2 - tbx;
    display.setCursor(x, y + tbh + 5);
    display.print(txt2);

    display.getTextBounds(txt3, 0, 0, &tbx, &tby, &tbw, &tbh);
    x = (EPD_W - tbw) / 2 - tbx;
    display.setCursor(x, y + 2 * (tbh + 5));
    display.print(txt3);
  } while (display.nextPage());
}
/* ---------- 刷图 ---------- */
void renderImageFromFlash(const char *bwPath, const char *redPath) {
  File f = SPIFFS.open(bwPath, FILE_READ);
  if (!f) { Serial.println("no bw file"); return; }
  uint8_t *bw = (uint8_t*)malloc(BUF_SIZE);
  f.read(bw, BUF_SIZE);
  f.close();

  f = SPIFFS.open(redPath, FILE_READ);
  if (!f) { Serial.println("no red file"); free(bw); return; }
  uint8_t *rd = (uint8_t*)malloc(BUF_SIZE);
  f.read(rd, BUF_SIZE);
  f.close();

  // 三色屏一次刷新
  display.firstPage();
  do {
    display.fillScreen(GxEPD_WHITE);
    display.drawBitmap(0, 0, bw, EPD_W, EPD_H, GxEPD_BLACK);
    display.drawBitmap(0, 0, rd, EPD_W, EPD_H, GxEPD_RED);
  } while (display.nextPage());

  free(bw);
  free(rd);
  Serial.println("Display refreshed");
}

/* ---------- 初始化 ---------- */
void setup() {
  Serial.begin(115200);

  if (!SPIFFS.begin(true)) {
    Serial.println("SPIFFS Mount Failed");
    return;
  }

  display.init();
  display.setRotation(0);
  display.fillScreen(GxEPD_WHITE);
  display.display();   // 清屏一次
  
  WiFi.softAPConfig(local_IP, gateway, subnet);
  WiFi.softAP(ssid, password);
  Serial.print("AP IP: "); Serial.println(WiFi.softAPIP());

  server.on("/upload", HTTP_POST,
            []() { server.send(200, "text/plain", "Upload complete"); },
            handleUpload);
  server.begin();
  showAPInfo();
}

void loop() {
  server.handleClient();
}