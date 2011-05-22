#!/bin/sh

usage() {
	echo "Usage: $0 gradleVersion(orig|head|wharf) [debug|cleanHome|sample] [extra params]"
	exit 1
}

if [ -z "$1" ]; then
	usage
fi

grVer=$1
shift;

rootPath=/work/jfrog/tools
gradlePath=$rootPath/gradle-$grVer
gradleHome=/tmp/gradle-home-$grVer
projectHome=build/multi

if [ ! -x "$gradlePath/bin/gradle" ]; then
	echo "ERROR: Did not find gradle at $gradlePath"
	usage
fi

if [ "$1" = "debug" ]; then
	shift;
	export GRADLE_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
fi

if [ "$1" = "cleanHome" ]; then
	shift;
	rm -rf $gradleHome
fi

if [ "$1" = "sample" ]; then
	shift;
	projectHome=../subprojects/docs/src/samples/multiRepo
fi


cd $projectHome && \
$gradlePath/bin/gradle --gradle-user-home=/tmp/gradle-home-$grVer $@

