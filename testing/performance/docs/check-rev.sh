#!/bin/bash
# script to be used for Gradle performance test bisecting
# example usage:
# git bisect start HEAD REL_2.14 --  # HEAD=bad REL_2.14=good
# git bisect run check_rev.sh JavaConfigurationPerformanceTest lotDependencies
TESTNAME=${1:-IdeIntegrationPerformanceTest}
TESTPROJECT=${2:-multi}
./gradlew clean
[ -d ~/.gradle-bisect-override ] && cp -Rdvp ~/.gradle-bisect-override/* .
[ -x ~/.gradle-bisect-override-script ] && ~/.gradle-bisect-override-script $TESTNAME $TESTPROJECT
./gradlew -S -PtimestampedVersion -x :performance:prepareSamples :performance:$TESTPROJECT :performance:cleanPerformanceTest :performance:performanceTest -D:performance:performanceTest.single=$TESTNAME
result=$?
hash=$(git rev-parse HEAD | colrm 9)
datets=$(date +%Y-%m-%d-%H:%M:%S)
[ -d ~/.gradle-bisect-results ] || mkdir ~/.gradle-bisect-results
cp subprojects/performance/build/test-results/performanceTest/TEST-org.gradle.performance.$TESTNAME.xml ~/.gradle-bisect-results/result_${result}_${hash}_${datets}.xml
git reset --hard
exit $result
