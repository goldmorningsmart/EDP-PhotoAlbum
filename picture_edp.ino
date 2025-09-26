#include <SPIFFS.h>
#include <WiFi.h>
#include <WebServer.h>
#include <GxEPD2_3C.h>

#define BUSY_Pin  8
#define RES_Pin   6
#define DC_Pin    9
#define CS_Pin    7
#define SCK_Pin   2
#define SDI_Pin   3

#define EPD_W  400
#define EPD_H  300
#define BUF_SIZE  (EPD_W * EPD_H / 8)   // 15000 字节

GxEPD2_3C<GxEPD2_420c_Z21, EPD_H> display(GxEPD2_420c_Z21(CS_Pin, DC_Pin, RES_Pin, BUSY_Pin));

const char *ssid     = "ESP32-Hotspot";
const char *password = "12345678";
IPAddress local_IP(192, 168, 1, 1);
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
  display.setRotation(1);
  display.fillScreen(GxEPD_WHITE);
  display.display();   // 清屏一次

  WiFi.softAPConfig(local_IP, gateway, subnet);
  WiFi.softAP(ssid, password);
  Serial.print("AP IP: "); Serial.println(WiFi.softAPIP());

  server.on("/upload", HTTP_POST,
            []() { server.send(200, "text/plain", "Upload complete"); },
            handleUpload);
  server.begin();
}

void loop() {
  server.handleClient();
}