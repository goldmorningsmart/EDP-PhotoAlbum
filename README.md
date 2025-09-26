# 📷 ESP32-C3 三色墨水屏电子相册

本项目是一个 **无线电子相册系统**，由硬件端（ESP32-C3 + 三色墨水屏）和软件端（Java 上位机）组成，支持通过 Wi-Fi 上传图片并在墨水屏上显示黑、白、红三色图像。

## ✨ 功能特性
- **硬件端**  
  - 采用 ESP32-C3 微控制器，低功耗、支持 Wi-Fi。  
  - 驱动三色墨水屏（黑 / 白 / 红），适合低刷新率显示相片或文字。  

- **软件端（上位机）**  
  - 使用 Java Swing 开发，提供图形化界面。  
  - 支持选择图片、转换为三色墨水屏格式。  
  - 通过 Wi-Fi 将图片上传到 ESP32-C3。  

## 🔧 技术栈
- **硬件**：ESP32-C3、三色 E-Paper 墨水屏  
- **固件**：ESP32 Arduino 框架、GxEPD2
- **上位机**：Java（Swing GUI + Wi-Fi 通信）  

## 🚀 使用方式
1. 编译并烧录 ESP32-C3 固件。  
2. 上电，等待墨水屏显示WIFI、密码和IP地址，然后连接墨水屏显示的WIFI。
3. 在 Java 上位机中选择图片，处理图片，点击上传，IP地址保持默认。  
4. 图片经处理后通过 Wi-Fi 发送到 ESP32-C3，并显示在墨水屏上。

![IMG_2739.jpeg](doc/1.jpeg)
![IMG_2643.jpeg](doc/2.jpeg)

可以使用其他4.2寸墨水屏的项目中的外壳，与甘草群的4.2寸外壳兼容。
* https://oshwhub.com/hjh70526/4-2-cun-mo-shui-ping-yue-du-qi
* https://oshwhub.com/z294933698/4-2-cun-mo-shui-ping-gai_copy


### **Arduino和上位机代码**

开源地址：https://github.com/goldmorningsmart/EDP-PhotoAlbum。

上位机使用java开发
![截屏2025-09-26 20.12.07.png](doc/3.png)

## 💻支持的墨水屏：
使用Arduino的GxEPD2库驱动，理论上GxEPD2能支持的墨水屏都可以支持。  

* GxEPD2：https://github.com/ZinggJM/GxEPD2

实测以下墨水屏可以使用： 

* WFT0420CZ15（使用GxEPD2_420c_Z21）  
* HINK-E042A13-A0（使用GxEPD2_420c_Z98，源码中的GxEPD2_420c_E042A13是我复制了一份改了个名字）
## ⚡️焊接说明：
当前版本的固件没有图片轮播和时钟功能，所以电池充电部分和时钟的元件可以不焊接，然后直接把VBUS和+5V的焊盘短接。

![截屏2025-09-26 19.25.42.png](https://image.lceda.cn/oshwhub/pullImage/0c136c884ffe4c88878abba9f14b8c47.png)





 




## 💻编译上位机
### 一、先编译成 JAR

```bash
# 编译
javac EPDUploaderSwing.java -d out

# 打包 JAR（指定主类）
jar --create --file EPDUploaderSwing.jar --main-class EPDUploaderSwing -C out .
```

### 二、用 jpackage 生成应用

 依赖需求：JDK ≥ 14

#### Windows 生成exe：

```bash
jlink \
  --module-path $JAVA_HOME/jmods \
  --add-modules java.base,java.desktop \
  --output runtime

jpackage --name EPDUploaderSwing \
  --input . \
  --main-jar EPDUploaderSwing.jar \
  --main-class EPDUploaderSwing \
  --type app-image\
   --runtime-image runtime\
  --icon EPDUploaderSwing.icon
```

生成目录类似：

```
EPDUploaderSwing/
├── bin/EPDUploaderSwing.exe
├── lib/（依赖jar）
```

直接运行 `bin/EPDUploaderSwing.exe` 就行。

---

#### macOS 生成app：

```bash
jlink \
  --module-path $JAVA_HOME/jmods \
  --add-modules java.base,java.desktop \
  --output runtime

jpackage --name EPDUploaderSwing \
  --input . \
  --main-jar EPDUploaderSwing.jar \
  --main-class EPDUploaderSwing \
  --type app-image\
   --runtime-image runtime\
  --icon EPDUploaderSwing.icns

```

生成的目录类似：

```
EPDUploaderSwing.app/
└── Contents/
    ├── Info.plist
    ├── MacOS/EPDUploaderSwing
    └── Resources/app
```

双击 `EPDUploaderSwing.app` 就能运行。

