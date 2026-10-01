#!/bin/bash
git clone https://github.com/cgutman/AdbLib.git
cd AdbLib
mkdir -p build/classes
# Force compilation to Java 8 (target/source 1.8)
javac -source 1.8 -target 1.8 -d build/classes src/com/cgutman/adblib/*.java
cd build/classes
jar cvf ../../adblib.jar .
cd ../../..
mkdir -p app/libs
mv AdbLib/adblib.jar app/libs/adblib.jar
rm -rf AdbLib
