@echo off
setlocal

REM 1. 清理旧目录并建立结构
rmdir /s /q build dist 2>nul

REM 2. 编译源代码到 classes
javac -d build\classes src\EPDUploaderSwing.java

REM 3. 打包成 JAR，指定入口类
jar -c -f build\jar\EPDUploaderSwing.jar -e EPDUploaderSwing -C build\classes .

REM 4. jlink：生成精简的自定义运行时
jlink ^
  --module-path "%JAVA_HOME%\jmods" ^
  --add-modules java.base,java.desktop ^
  --output build\runtime

REM 5. jpackage：打包 app-image
jpackage ^
  --name EPDUploaderSwing ^
  --input build\jar ^
  --main-jar EPDUploaderSwing.jar ^
  --type app-image ^
  --runtime-image build\runtime ^
  --icon EPDUploaderSwing.ico ^
  --dest dist

echo ✅ 构建完成，应用输出在 dist\EPDUploaderSwing
pause
