#!/usr/bin/env bash
# Needs only a JDK and kotlinc.
set -e
cd "$(dirname "$0")/../.."
SRC=app/src/main/java/com/zlitch/clackwell
kotlinc tools/tests/LogicTest.kt $SRC/TypingLogic.kt $SRC/Layouts.kt $SRC/Suggester.kt -include-runtime -d build/logic-test.jar
java -jar build/logic-test.jar
