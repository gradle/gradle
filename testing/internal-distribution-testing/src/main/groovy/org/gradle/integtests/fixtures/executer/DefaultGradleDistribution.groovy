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
        25: "9.1.0",
        26: "9.4.0",
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
    boolean clientWorksWith(int jvmVersion) {
        if (!isSupportedGradleVersion()) {
            return false
        }

        return jvmVersion >= getMinSupportedClientJavaVersion() && jvmVersion <= getMaxSupportedJavaVersion()
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
    boolean isSupportedGradleVersion() {
        // TODO: Make this an assertion so we don't accidentally skip test coverage
        // when we think we are running tests against old versions
        return version >= DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION
    }

    private int getMaxSupportedJavaVersion() {
        return findHighestSupportedKey(MAX_SUPPORTED_JAVA_VERSIONS)
            .orElse(8) // Java 8 support was added in Gradle 2.0
    }

    private int getMinSupportedClientJavaVersion() {
        return findHighestSupportedKey(MIN_SUPPORTED_CLIENT_JAVA_VERSIONS)
            .orElse(7) // Java 7 has been required since Gradle 3.0
    }

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
