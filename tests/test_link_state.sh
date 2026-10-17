#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT HUP INT TERM
javac -encoding UTF-8 --release 8 -d "$temp_dir" src/cn/usbdoctor/LinkState.java tests/LinkStateTest.java
java -cp "$temp_dir" cn.usbdoctor.LinkStateTest
