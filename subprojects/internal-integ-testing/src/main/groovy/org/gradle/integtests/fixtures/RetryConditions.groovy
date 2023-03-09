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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion

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

        // known issue with pre 1.3 daemon versions: https://github.com/gradle/gradle/commit/29d895bc086bc2bfcf1c96a6efad22c602441e26
        if (targetDistVersion < GradleVersion.version("1.3") &&
            (failure.cause?.message ==~ /(?s)Timeout waiting to connect to (the )?Gradle daemon.*/
                || failure.cause?.message == "Gradle build daemon disappeared unexpectedly (it may have been stopped, killed or may have crashed)"
                || failure.message == "Gradle build daemon disappeared unexpectedly (it may have been stopped, killed or may have crashed)")) {
            println "Retrying cross version test because of <1.3 daemon connection issue"
            return cleanProjectDir(specification)
        }

        // this is cause by a bug in Gradle <1.8, where a NPE is thrown when DaemonInfo is removed from the daemon registry by another process
        if (targetDistVersion < GradleVersion.version("1.8") &&
            failure.getClass().getSimpleName() == 'GradleConnectionException' && failure.cause.getClass().getSimpleName() == 'NullPointerException') {
            return cleanProjectDir(specification)
        }

        if (targetDistVersion < GradleVersion.version('2.10')) {
            if (getRootCauseMessage(failure) ==~ /Unable to calculate percentage: .* of .*\. All inputs must be >= 0/) {
                println "Retrying cross version test because of timing issue in Gradle versions <2.10"
                return cleanProjectDir(specification)
            }
        }

        if (targetDistVersion == GradleVersion.version('1.9') || targetDistVersion == GradleVersion.version('1.10')) {
            if (failure.class.simpleName == 'ServiceCreationException'
                && failure.cause?.class?.simpleName == 'UncheckedIOException'
                && failure.cause?.message == "Unable to create directory 'metadata-2.1'") {

                println "Retrying cross version test for " + targetDistVersion.version + " because failure was caused by directory creation race condition"
                return cleanProjectDir(specification)
            }
        }

        // daemon connection issue that does not appear anymore with 3.x versions of Gradle
        if (targetDistVersion < GradleVersion.version("3.0") &&
            failure.cause?.message ==~ /(?s)Timeout waiting to connect to (the )?Gradle daemon\..*/) {

            println "Retrying cross version test because daemon connection is broken."
            return cleanProjectDir(specification)
        }

        // known problem with Gradle versions < 3.5
        // See https://github.com/gradle/gradle-private/issues/744
        if (targetDistVersion < GradleVersion.version('3.5') && daemonsFixture != null && getRootCauseMessage(failure) == 'Build cancelled.') {
            for (daemon in daemonsFixture.daemons) {
                if (daemon.logContains('Could not receive message from client.')
                    && daemon.logContains('java.lang.NullPointerException')
                    && daemon.logContains('org.gradle.launcher.daemon.server.exec.LogToClient')) {
                    println "Retrying test because the dispatcher was not ready for receiving a log event. Check log of daemon with PID ${daemon.context.pid}."
                    return cleanProjectDir(specification)
                }
            }
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
        if (runsOnWindowsAndJava7or8() && daemonsFixture != null) {
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
        runsOnWindowsAndJava7or8() && (daemon.logContains("java.net.SocketException: Socket operation on nonsocket:")
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

    static boolean runsOnWindowsAndJava7or8() {
        def windowsPrecondition = new UnitTestPreconditions.Windows()
        return windowsPrecondition.satisfied && [JavaVersion.VERSION_1_7, JavaVersion.VERSION_1_8].contains(JavaVersion.current())
    }
}
