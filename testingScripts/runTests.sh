#!/usr/bin/env bash

PACKAGE=$1

echo $PACKAGE

./gradlew test -p flowTests --debug-jvm --tests $PACKAGE