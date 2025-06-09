/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.cache.internal.CacheVersion
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.util.GradleVersion

class DefaultGradleDistribution implements GradleDistribution {

    /**
     * The java version mapped to the first Gradle version that supports it.
     *
     * @see <a href="https://docs.gradle.org/current/userguide/compatibility.html#java_runtime">link</a>
     */
    private static final TreeMap<Integer, String> MAX_SUPPORTED_JAVA_VERSIONS = [
        9: "4.3",
        10: "4.7",
        11: "5.0",
        12: "5.4", // 5.4 officially added support for JDK 12, but it worked before then.
        13: "6.0",
        14: "6.3",
        15: "6.7",
        16: "7.0",
        17: "7.3",
        18: "7.5",
        19: "7.6",
        20: "8.3",
        21: "8.5",
        22: "8.8",
        23: "8.10",
        24: "8.14",
    ]

    /**
     * The java version mapped to the first Gradle version that required it as
     * a minimum for the daemon.
     */
    private static final TreeMap<Integer, String> MIN_SUPPORTED_DAEMON_JAVA_VERSIONS = [
        8: "5.0",
        17: "9.0",
    ]

    /**
     * The java version mapped to the first Gradle version that required it as
     * a minimum for clients.
     */
    private static final TreeMap<Integer, String> MIN_SUPPORTED_CLIENT_JAVA_VERSIONS = [
        8: "5.0",
    ]

    private final GradleVersion version;
    private final TestFile gradleHomeDir;
    private final TestFile binDistribution;

    DefaultGradleDistribution(GradleVersion gradleVersion, TestFile gradleHomeDir, TestFile binDistribution) {
        this.version = gradleVersion;
        this.gradleHomeDir = gradleHomeDir;
        this.binDistribution = binDistribution;
    }

    @Override
    String toString() {
        return version.toString();
    }

    @Override
    TestFile getGradleHomeDir() {
        return gradleHomeDir;
    }

    @Override
    TestFile getBinDistribution() {
        return binDistribution;
    }

    @Override
    GradleVersion getVersion() {
        return version;
    }

    @Override
    GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        return new NoDaemonGradleExecuter(this, testDirectoryProvider, version, buildContext)
    }

    @Override
<<<<<<< HEAD
    boolean worksWith(Jvm jvm) {
        // Milestone 4 was broken on the IBM jvm
        if (jvm.isIbmJvm() && isVersion("1.0-milestone-4")) {
            return false;
        }

        Integer javaVersion = jvm.javaVersionMajor
        if (javaVersion == null) {
            throw new IllegalArgumentException()
        }

        return daemonWorksWith(javaVersion)
    }

    @Override
    boolean daemonWorksWith(int javaVersion) {
        // 0.9-rc-1 was broken for Java 5
        if (isVersion("0.9-rc-1") && javaVersion == 5) {
            return false
        }

        if (isSameOrOlder("1.0")) {
            return javaVersion >= 5 && javaVersion <= 7
        }

        // 1.x works on Java 5 - 8
        if (isSameOrOlder("1.12")) {
            return javaVersion >= 5 && javaVersion <= 8
        }

        // 2.x and 3.0-milestone-1 work on Java 6 - 8
        if (isSameOrOlder("3.0-milestone-1")) {
            return javaVersion >= 6 && javaVersion <= 8
        }

        // 3.x - 4.6 works on Java 7 - 8
        if (isSameOrOlder("4.6")) {
            return javaVersion >= 7 && javaVersion <= 8
        }

        if (isSameOrOlder("4.11")) {
            return javaVersion >= 7 && javaVersion <= 10
        }

        // 5.4 officially added support for JDK 12, but it worked before then.
        if (isSameOrOlder("5.7")) {
            return javaVersion >= 8 && javaVersion <= 12
        }

        if (isSameOrOlder("6.0")) {
            return javaVersion >= 8 && javaVersion <= 13
        }

        // 6.7 added official support for JDK15
        if (isSameOrOlder("6.6.1")) {
            return javaVersion >= 8 && javaVersion <= 14
        }

        // 7.0 added official support for JDK16
        // milestone 2 was published with Groovy 3 upgrade and without asm upgrade yet
        // subsequent milestones and RCs will support JDK16
        if (isSameOrOlder("7.0-milestone-2")) {
            return javaVersion >= 8 && javaVersion <= 15
        }

        // 7.3 added JDK 17 support
        if (isSameOrOlder("7.2")) {
            return javaVersion >= 8 && javaVersion <= 16
        }

        // 7.5 added JDK 18 support
        if (isSameOrOlder("7.4.2")) {
            return javaVersion >= 8 && javaVersion <= 17
        }

        // 7.6 added JDK 19 support
        if (isSameOrOlder("7.5.1")) {
            return javaVersion >= 8 && javaVersion <= 18
        }

        // 8.3 added JDK 20 support
        if (isSameOrOlder("8.2.1")) {
            return javaVersion >= 8 && javaVersion <= 19
        }

        // 8.5 added JDK 21 support
        if (isSameOrOlder("8.4")) {
            return javaVersion >= 8 && javaVersion <= 20
        }

        // 8.8 added JDK 22 support
        if (isSameOrOlder("8.7")) {
            return javaVersion >= 8 && javaVersion <= 21
        }

        // 8.10 added JDK 23 support
        if (isSameOrOlder("8.9")) {
            return javaVersion >= 8 && javaVersion <= 22
        }

        // 8.14 added JDK 24 support
        if (isSameOrOlder("8.13")) {
            return javaVersion >= 8 && javaVersion <= 23
        }

        // 9.0+ requires Java 17
        if (isSameOrOlder("8.14")) {
            return javaVersion >= 8 && javaVersion <= 24
        }

        return javaVersion >= 17 && maybeEnforceHighestVersion(javaVersion, 24)
=======
    boolean clientWorksWith(int jvmVersion) {
        if (!isSupportedGradleVersion()) {
            return false
        }

        return jvmVersion >= getMinSupportedClientJavaVersion() && jvmVersion <= getMaxSupportedJavaVersion()
>>>>>>> master
    }

    @Override
    boolean daemonWorksWith(int jvmVersion) {
        if (!isSupportedGradleVersion()) {
            return false
        }

        return jvmVersion >= getMinSupportedDaemonJavaVersion() && jvmVersion <= getMaxSupportedJavaVersion()
    }

    /**
     * Return true if this Gradle version is supported for cross version tests.
     * <p>
     * If you hit this condition and your tests are not executing, you should update
     * your tests to not run against this version.
     */
<<<<<<< HEAD
    private static boolean maybeEnforceHighestVersion(int javaVersion, int highestVersion) {
        boolean disableHighest = System.getProperty(DISABLE_HIGHEST_JAVA_VERSION) != null
        return disableHighest || javaVersion <= highestVersion
=======
    boolean isSupportedGradleVersion() {
        // TODO: Make this an assertion so we don't accidentally skip test coverage
        // when we think we are running tests against old versions
        return version >= DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION
>>>>>>> master
    }

    private int getMaxSupportedJavaVersion() {
        return findHighestSupportedKey(MAX_SUPPORTED_JAVA_VERSIONS)
            .orElse(8) // Java 8 support was added in Gradle 2.0
    }

    private int getMinSupportedClientJavaVersion() {
        return findHighestSupportedKey(MIN_SUPPORTED_CLIENT_JAVA_VERSIONS)
            .orElse(7) // Java 7 has been required since Gradle 3.0
    }

<<<<<<< HEAD
    @Override
    boolean isToolingApiLocksBuildActionClasses() {
        return isSameOrOlder("3.0");
    }

    @Override
    boolean isToolingApiLoggingInEmbeddedModeSupported() {
        return isSameOrNewer("2.9-rc-1");
=======
    private int getMinSupportedDaemonJavaVersion() {
        return findHighestSupportedKey(MIN_SUPPORTED_DAEMON_JAVA_VERSIONS)
            .orElse(7) // Java 7 has been required since Gradle 3.0
    }

    /**
     * Find the highest key such that the corresponding value is the
     * same or newer than the current Gradle version.
     */
    private Optional<Integer> findHighestSupportedKey(NavigableMap<Integer, String> versionMap) {
        return versionMap.descendingMap().entrySet().stream()
            .filter { isSameOrNewer(it.value) }
            .findFirst()
            .map { it.key }
>>>>>>> master
    }

    @Override
    boolean isToolingApiStdinInEmbeddedModeSupported() {
        return isSameOrNewer("5.6-rc-1");
    }

    @Override
    CacheVersion getArtifactCacheLayoutVersion() {
        return CacheLayout.META_DATA.getVersionMapping().getVersionUsedBy(this.version).get()
    }

    @Override
    boolean isAddsTaskExecutionExceptionAroundAllTaskFailures() {
        return isSameOrNewer("5.0");
    }

    @Override
    boolean isToolingApiRetainsOriginalFailureOnCancel() {
        // Versions before 5.1 would unpack the exception and throw part of it, losing some context
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    boolean isToolingApiDoesNotAddCausesOnTaskCancel() {
        // Versions before 5.1 would sometimes add some additional 'build cancelled' exceptions
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    boolean isToolingApiHasCauseOnCancel() {
        // There was a regression in 4.0.x
        return isSameOrNewer("4.1")
    }

    @Override
    boolean isToolingApiHasCauseOnForcedCancel() {
        // Versions before 5.1 would discard context on forced cancel
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    boolean isToolingApiLogsFailureOnCancel() {
        // Versions before 4.1 would log "CONFIGURE SUCCESSFUL" for model/action execution (but "BUILD FAILED" for task/test execution)
        return isSameOrNewer("4.1");
    }

    @Override
    boolean isToolingApiHasCauseOnPhasedActionFail() {
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    boolean isToolingApiMergesStderrIntoStdout() {
        return isSameOrNewer("4.7") && isSameOrOlder("5.0");
    }

    @Override
    boolean isToolingApiHasExecutionPhaseBuildOperation() {
        return isSameOrNewer("7.1-rc-1");
    }

    @Override
    boolean isRunsBuildSrcTests() {
        return isSameOrOlder("7.6")
    }

    @Override
    <T> T selectOutputWithFailureLogging(T stdout, T stderr) {
        if (isSameOrOlder("4.6") || isSameOrNewer("5.1-rc-1")) {
            return stderr;
        }
        return stdout;
    }

    @Override
    boolean isSupportsKotlinScript() {
        return isSameOrNewer("4.10.3"); // see compatibility matrix https://docs.gradle.org/8.0/userguide/compatibility.html
    }

    @Override
    boolean isSupportsCustomToolchainResolvers() {
        return isSameOrNewer("7.6")
    }

    @Override
    boolean isNonFlakyToolchainProvisioning() {
        // Excluding potential 8.9 RCs
        return !isSameOrOlder("8.8")
    }

    private boolean isNewer(String otherVersion) {
        version > GradleVersion.version(otherVersion)
    }

    protected boolean isOlder(String otherVersion) {
        version < GradleVersion.version(otherVersion)
    }

    protected boolean isSameOrNewer(String otherVersion) {
        return isVersion(otherVersion) || isNewer(otherVersion)
    }

    protected boolean isSameOrOlder(String otherVersion) {
        return isVersion(otherVersion) || isOlder(otherVersion)
    }

    protected boolean isVersion(String otherVersionString) {
        GradleVersion otherVersion = GradleVersion.version(otherVersionString);
        return version.compareTo(otherVersion) == 0 || (version.isSnapshot() && version.getBaseVersion().equals(otherVersion.getBaseVersion()));
    }
}
