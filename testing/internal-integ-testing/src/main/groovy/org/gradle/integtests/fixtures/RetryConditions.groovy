/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import org.junit.AssumptionViolatedException

import javax.annotation.Nullable

class RetryConditions {

    static private final String[] FILES_TO_PRESERVE = ['reproducible-archives-init.gradle']

    static boolean onContinuousBuildTimeout(Object specification, Throwable t) {
        def targetVersion = extractTargetVersionFromToolingApiSpecification(specification)
        if (targetVersion == null || targetVersion.baseVersion >= GradleVersion.version("7.5")) {
            // Starting with Gradle 7.5 we are using a new continuous build infrastructure,
            // which shouldn't be flaky any more.
            return false
        }
        if (t?.message?.startsWith('Timeout waiting for build to complete.')) {
            println "Retrying continuous build test because of timeout"
            return cleanProjectDir(specification)
        }
        false
    }

    static boolean cleanProjectDir(Object specification) {
        if (specification.hasProperty("toolingApi")) {
            specification.toolingApi.cleanUpIsolatedDaemonsAndServices()
        }
        if (specification.hasProperty("caughtGradleConnectionException")) {
            specification.caughtGradleConnectionException = null
        }
        if (specification.hasProperty("projectDir")) {
            specification.projectDir.listFiles().each {
                if (!FILES_TO_PRESERVE.contains(it.name)) {
                    it.deleteDir()
                }
            }
        } else if (specification.hasProperty("testDirectory")) {
            specification.testDirectory.listFiles().each {
                if (!FILES_TO_PRESERVE.contains(it.name)) {
                    it.deleteDir()
                }
            }
        } else if (specification.hasProperty("temporaryFolder")) {
            specification.temporaryFolder.testDirectory.listFiles().each {
                if (!FILES_TO_PRESERVE.contains(it.name)) {
                    it.deleteDir()
                }
            }
        }
        true
    }

    static boolean onIssueWithReleasedGradleVersion(Object specification, Throwable failure) {
        if (failure instanceof AssumptionViolatedException) {
            return false
        }

        def daemonsFixture = specification.hasProperty("daemonsFixture") ? specification.daemonsFixture : null
        return shouldRetry(specification, failure, daemonsFixture)
    }

    private static boolean shouldRetry(Object specification, Throwable failure, @Nullable DaemonLogsAnalyzer daemonsFixture) {
        def caughtGradleConnectionException = specification.hasProperty("caughtGradleConnectionException") ? specification.caughtGradleConnectionException : null

        println "Failure: " + failure
        println "Cause  : " + failure?.cause

        if (caughtGradleConnectionException != null) {
            failure = caughtGradleConnectionException
            println "Failure (caught during test): " + failure
            println "Cause   (caught during test): " + failure?.cause
        }

        println "Daemons (potentially used): ${daemonsFixture?.allDaemons?.collect { it.context?.pid }} - ${daemonsFixture?.daemonBaseDir}"

        def targetDistVersion = extractTargetVersionFromToolingApiSpecification(specification)
        if (targetDistVersion == null) {
            println "Can not retry cross version test because 'gradleVersion' is unknown"
            return false
        }

        // sometime sockets are unexpectedly disappearing on daemon side (running on windows): https://github.com/gradle/gradle/issues/1111
        didSocketDisappearOnWindows(failure, specification, daemonsFixture, targetDistVersion >= GradleVersion.version('3.0'))
    }

    @Nullable
    private static GradleVersion extractTargetVersionFromToolingApiSpecification(specification) {
        String targetGradleVersion = specification.hasProperty("releasedGradleVersion") ? specification.releasedGradleVersion : null
        println "Cross version test failure with target version " + targetGradleVersion
        return targetGradleVersion == null
            ? null
            : GradleVersion.version(targetGradleVersion)
    }

    static void onWindowsSocketDisappearance(Object specification, Throwable failure) {
        def daemonFixture = specification.hasProperty("daemonsFixture") ? specification.daemonsFixture : null
        didSocketDisappearOnWindows(failure, specification, daemonFixture)
    }

    static private boolean didSocketDisappearOnWindows(Throwable failure, Object specification, daemonsFixture, checkDaemonLogs = true) {
        // sometime sockets are unexpectedly disappearing on daemon side (running on windows): gradle/gradle#1111
        if (isAffectedBySocketDisappearanceIssue() && daemonsFixture != null) {
            if (getRootCauseMessage(failure) == "An existing connection was forcibly closed by the remote host" ||
                getRootCauseMessage(failure) == "An established connection was aborted by the software in your host machine" ||
                getRootCauseMessage(failure) == "Connection refused: no further information") {

                if (!checkDaemonLogs) {
                    println "Retrying test because socket disappeared."
                    return cleanProjectDir(specification)
                }

                for (def daemon : daemonsFixture.allDaemons) {
                    if (daemonStoppedWithSocketExceptionOnWindows(daemon)) {
                        println "Retrying test because socket disappeared. Check log of daemon with PID ${daemon.context.pid}."
                        return cleanProjectDir(specification)
                    }
                }
            }
        }
        false
    }

    static daemonStoppedWithSocketExceptionOnWindows(daemon) {
        isAffectedBySocketDisappearanceIssue() && (daemon.logContains("java.net.SocketException: Socket operation on nonsocket:")
            || daemon.logContains("java.io.IOException: An operation was attempted on something that is not a socket")
            || daemon.logContains("java.io.IOException: An existing connection was forcibly closed by the remote host"))
    }

    static String getRootCauseMessage(Throwable throwable) {
        final List<Throwable> list = getThrowableList(throwable)
        return list.size() < 2 ? "" : list.get(list.size() - 1).message
    }

    static List<Throwable> getThrowableList(Throwable throwable) {
        final List<Throwable> list = new ArrayList<Throwable>()
        while (throwable != null && !list.contains(throwable)) {
            list.add(throwable)
            throwable = throwable.cause
        }
        list
    }

    static boolean isAffectedBySocketDisappearanceIssue() {
        return new UnitTestPreconditions.IsKnownWindowsSocketDisappearanceIssue().isSatisfied()
    }
}
