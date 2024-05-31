
#
# Copyright 2024 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

BASE_DIR=$PWD/tmp

./gradlew dist-core:binDist wrapper-main:jar tooling-api:toolingApiShadedJar $* || exit 1

create_dir() {
    DIR_PATH=$1
    if [ -d $DIR_PATH ]; then
        rm -rf $DIR_PATH
    fi
    mkdir -p $DIR_PATH
}

create_dir tmp

DIST_DIR=tmp/dist
create_dir $DIST_DIR
(cd $DIST_DIR && unzip ../../platforms/core-runtime/distributions-core/build/distributions/gradle-8.9-bin.zip) || exit 1

CLI_JAR_DIR=tmp/cli-jar
create_dir $CLI_JAR_DIR
(cd $CLI_JAR_DIR && unzip ../dist/*/lib/gradle-cli-main-8.9.jar) || exit 1

WRAPPER_DIR=tmp/wrapper-main-jar
create_dir $WRAPPER_DIR
(cd $WRAPPER_DIR && unzip ../../platforms/core-runtime/wrapper-main/build/libs/gradle-wrapper-main-8.9.jar) || exit 1

WRAPPER_JAR_DIR=tmp/wrapper-jar
create_dir $WRAPPER_JAR_DIR
(cd $WRAPPER_JAR_DIR && unzip ../wrapper-main-jar/gradle-wrapper.jar) || exit 1

LAUNCHER_JAR_DIR=tmp/launcher-jar
create_dir $LAUNCHER_JAR_DIR
(cd $LAUNCHER_JAR_DIR && unzip ../dist/*/lib/gradle-launcher-8.9.jar) || exit 1

DAEMON_MAIN_JAR_DIR=tmp/daemon-main-jar
create_dir $DAEMON_MAIN_JAR_DIR
(cd $DAEMON_MAIN_JAR_DIR && unzip ../dist/*/lib/gradle-daemon-main-8.9.jar) || exit 1

TOOLING_API_DIR=tmp/tapi
TOOLING_API_JAR=platforms/ide/tooling-api/build/shaded-jar/gradle-tooling-api-shaded-8.9.jar
create_dir $TOOLING_API_DIR
unzip -l ~/gradle/projects/gradle/platforms/ide/tooling-api/build/shaded-jar/gradle-tooling-api-shaded-8.9.jar | awk '{ print $4 }' | sort > $TOOLING_API_DIR/before.txt
unzip -l $TOOLING_API_JAR | awk '{ print $4 }' | sort > $TOOLING_API_DIR/after.txt

echo
echo "CLI manifest"
cat $CLI_JAR_DIR/META-INF/MANIFEST.MF

echo "Wrapper main manifest"
cat $WRAPPER_DIR/META-INF/MANIFEST.MF

echo "Wrapper manifest"
cat $WRAPPER_JAR_DIR/META-INF/MANIFEST.MF

echo "Launcher manifest"
cat $LAUNCHER_JAR_DIR/META-INF/MANIFEST.MF

echo "Daemon main manifest"
cat $DAEMON_MAIN_JAR_DIR/META-INF/MANIFEST.MF

echo "Tooling API JAR: `tree --list -d $TOOLING_API_JAR | awk ' /jar/ { print $1 } '`"
unzip -l $TOOLING_API_JAR | tail -n 1 | awk ' { print $1 " bytes, " $2 " " $3 } '

echo
