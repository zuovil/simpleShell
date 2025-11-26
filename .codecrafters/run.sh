#!/bin/sh
#
# This script is used to run your program on CodeCrafters
#
# This runs after .codecrafters/compile.sh
#
# Learn more: https://codecrafters.io/program-interface

set -e # Exit on failure

exec java --enable-preview --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar /tmp/codecrafters-build-shell-java/codecrafters-shell.jar "$@"
