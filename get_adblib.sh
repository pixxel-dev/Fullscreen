#!/bin/bash
git clone https://github.com/cgutman/AdbLib.git
cd AdbLib
mkdir -p build/classes
javac -d build/classes src/com/cgutman/adblib/*.java
cd build/classes
jar cvf ../../adblib.jar .
cd ../../..
mv AdbLib/adblib.jar app/libs/adblib.jar
rm -rf AdbLib
