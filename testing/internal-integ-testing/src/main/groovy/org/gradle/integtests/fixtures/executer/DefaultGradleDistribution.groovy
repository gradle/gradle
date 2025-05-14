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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.cache.internal.CacheVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
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
    boolean worksWith(Jvm jvm) {
        // Milestone 4 was broken on the IBM jvm
        if (jvm.isIbmJvm() && isVersion("1.0-milestone-4")) {
            return false;
        }

        JavaVersion javaVersion = jvm.getJavaVersion();
        if (javaVersion == null) {
            throw new IllegalArgumentException();
        }

        return GradleJavaCompatibility.isGradleCompatibleWithJava(version, javaVersion);
    }

    @Override
    boolean worksWith(OperatingSystem os) {
        // 1.0-milestone-5 was broken where jna was not available
        //noinspection SimplifiableIfStatement
        if (isVersion("1.0-milestone-5")) {
            return os.isWindows() || os.isMacOsX() || os.isLinux();
        } else {
            return true;
        }
    }

    @Override
    boolean isDaemonIdleTimeoutConfigurable() {
        return isSameOrNewer("1.0-milestone-7");
    }

    @Override
    boolean isToolingApiSupported() {
        return isSameOrNewer("1.0-milestone-3");
    }

    @Override
    boolean isToolingApiTargetJvmSupported(JavaVersion javaVersion) {
        return doesWorkWith(javaVersion);
    }

    @Override
    boolean isToolingApiLocksBuildActionClasses() {
        return isSameOrOlder("3.0");
    }

    @Override
    boolean isToolingApiLoggingInEmbeddedModeSupported() {
        return isSameOrNewer("2.9-rc-1");
    }

    @Override
    boolean isToolingApiStdinInEmbeddedModeSupported() {
        return isSameOrNewer("5.6-rc-1");
    }

    @Override
    CacheVersion getArtifactCacheLayoutVersion() {
        if (isSameOrNewer("1.9-rc-2")) {
            return CacheLayout.META_DATA.getVersionMapping().getVersionUsedBy(this.version).get();
        } else if (isSameOrNewer("1.9-rc-1")) {
            return CacheVersion.parse("1.31");
        } else if (isSameOrNewer("1.7-rc-1")) {
            return CacheVersion.parse("0.26");
        } else if (isSameOrNewer("1.6-rc-1")) {
            return CacheVersion.parse("0.24");
        } else if (isSameOrNewer("1.4-rc-1")) {
            return CacheVersion.parse("0.23");
        } else if (isSameOrNewer("1.3")) {
            return CacheVersion.parse("0.15");
        } else {
            return CacheVersion.parse("0.1");
        }
    }

    @Override
    boolean wrapperCanExecute(GradleVersion version) {
        if (version.equals(GradleVersion.version("0.8")) || isVersion("0.8")) {
            // There was a breaking change after 0.8
            return false;
        }
        if (isVersion("0.9.1")) {
            // 0.9.1 couldn't handle anything with a timestamp whose timezone was behind GMT
            return version.getVersion().matches(".*+\\d{4}");
        }
        if (isSameOrNewer("0.9.2") && isSameOrOlder("1.0-milestone-2")) {
            // These versions couldn't handle milestone patches
            if (version.getVersion().matches("1.0-milestone-\\d+[a-z]-.+")) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean isWrapperSupportsGradleUserHomeCommandLineOption() {
        return isSameOrNewer("1.7");
    }

    @Override
    boolean isSupportsSpacesInGradleAndJavaOpts() {
        return isSameOrNewer("1.0-milestone-5");
    }

    @Override
    boolean isFullySupportsIvyRepository() {
        return isSameOrNewer("1.0-milestone-7");
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
        // Versions before 3.2 would throw away the cause. There was also a regression in 4.0.x
        return isSameOrNewer("3.2") && !(isSameOrNewer("4.0") && isSameOrOlder("4.0.2"));
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
    boolean isToolingApiLogsConfigureSummary() {
        return isSameOrNewer("2.14");
    }

    @Override
    boolean isToolingApiHasExecutionPhaseBuildOperation() {
        return isSameOrNewer("7.1-rc-1");
    }

    @Override
    boolean isLoadsFromConfigurationCacheAfterStore() {
        return isSameOrNewer("8.0-milestone-5")
    }

    @Override
    boolean isRunsBuildSrcTests() {
        return isSameOrOlder("7.6")
    }

    @Override
    <T> T selectOutputWithFailureLogging(T stdout, T stderr) {
        if (isSameOrNewer("4.0") && isSameOrOlder("4.6") || isSameOrNewer("5.1-rc-1")) {
            return stderr;
        }
        return stdout;
    }

    @Override
    boolean isSupportsKotlinScript() {
        return isSameOrNewer("4.10.3"); // see compatibility matrix https://docs.gradle.org/8.0/userguide/compatibility.html
    }

    @Override
    boolean isHasTestDisplayNames() {
        return isSameOrNewer("8.8-rc-1")
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
