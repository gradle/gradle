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
import org.gradle.util.GradleVersion

class DefaultGradleDistribution implements GradleDistribution {
    private static final String DISABLE_HIGHEST_JAVA_VERSION = "org.gradle.java.version.disableHighest";
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
    boolean daemonWorksWith(int javaVersion) {
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
    }

    /**
     * Returns true if the given java version is less than the given highest version bound.  Always returns
     * true if the highest version check is disabled via system property.
     */
    private static boolean maybeEnforceHighestVersion(int javaVersion, int highestVersion) {
        boolean disableHighest = System.getProperty(DISABLE_HIGHEST_JAVA_VERSION) != null
        return disableHighest || javaVersion <= highestVersion
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

    protected boolean isSameOrNewer(String otherVersion) {
        return isVersion(otherVersion) || version.compareTo(GradleVersion.version(otherVersion)) > 0;
    }

    protected boolean isSameOrOlder(String otherVersion) {
        return isVersion(otherVersion) || version.compareTo(GradleVersion.version(otherVersion)) <= 0;
    }

    protected boolean isVersion(String otherVersionString) {
        GradleVersion otherVersion = GradleVersion.version(otherVersionString);
        return version.compareTo(otherVersion) == 0 || (version.isSnapshot() && version.getBaseVersion().equals(otherVersion.getBaseVersion()));
    }
}
