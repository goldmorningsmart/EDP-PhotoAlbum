## 一、先编译成 JAR

```bash
# 编译
javac EPDUploaderSwing.java -d out

# 打包 JAR（指定主类）
jar --create --file EPDUploaderSwing.jar --main-class EPDUploaderSwing -C out .
```

## 二、用 jpackage 生成裸应用

 JDK ≥ 14，有 `jpackage` 命令。

### Windows 生成裸 exe：

```bash
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

### macOS 生成裸 app：

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

