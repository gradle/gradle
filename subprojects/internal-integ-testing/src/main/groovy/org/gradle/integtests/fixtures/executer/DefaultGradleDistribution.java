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

package org.gradle.integtests.fixtures.executer;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout;
import org.gradle.cache.internal.CacheVersion;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;

public class DefaultGradleDistribution implements GradleDistribution {

    private final GradleVersion version;
    private final TestFile gradleHomeDir;
    private final TestFile binDistribution;

    public DefaultGradleDistribution(GradleVersion gradleVersion, TestFile gradleHomeDir, TestFile binDistribution) {
        this.version = gradleVersion;
        this.gradleHomeDir = gradleHomeDir;
        this.binDistribution = binDistribution;
    }

    @Override
    public String toString() {
        return version.toString();
    }

    @Override
    public TestFile getGradleHomeDir() {
        return gradleHomeDir;
    }

    @Override
    public TestFile getBinDistribution() {
        return binDistribution;
    }

    @Override
    public GradleVersion getVersion() {
        return version;
    }

    @Override
    public GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        return new NoDaemonGradleExecuter(this, testDirectoryProvider, version, buildContext).withWarningMode(null);
    }

    @Override
    public boolean worksWith(Jvm jvm) {
        // Milestone 4 was broken on the IBM jvm
        if (jvm.isIbmJvm() && isVersion("1.0-milestone-4")) {
            return false;
        }

        JavaVersion javaVersion = jvm.getJavaVersion();
        if (javaVersion == null) {
            throw new IllegalArgumentException();
        }

        return worksWith(javaVersion);
    }

    private boolean worksWith(JavaVersion javaVersion) {
        // 0.9-rc-1 was broken for Java 5
        if (isVersion("0.9-rc-1") && javaVersion == JavaVersion.VERSION_1_5) {
            return false;
        }
        
        if (isSameOrOlder("1.0")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_5) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_7) <= 0;
        }

        // 1.x works on Java 5 - 8
        if (isSameOrOlder("1.12")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_5) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        // 2.x and 3.0-milestone-1 work on Java 6 - 8
        if (isSameOrOlder("3.0-milestone-1")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_6) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        // 3.x - 4.6 works on Java 7 - 8
        if(isSameOrOlder("4.6")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_7) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        if (isSameOrOlder("4.10")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_7) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_10) <= 0;
        }

        return javaVersion.compareTo(JavaVersion.VERSION_1_8) >= 0;
    }

    @Override
    public boolean worksWith(OperatingSystem os) {
        // 1.0-milestone-5 was broken where jna was not available
        //noinspection SimplifiableIfStatement
        if (isVersion("1.0-milestone-5")) {
            return os.isWindows() || os.isMacOsX() || os.isLinux();
        } else {
            return true;
        }
    }

    @Override
    public boolean isDaemonIdleTimeoutConfigurable() {
        return isSameOrNewer("1.0-milestone-7");
    }

    @Override
    public boolean isToolingApiSupported() {
        return isSameOrNewer("1.0-milestone-3");
    }

    @Override
    public boolean isToolingApiTargetJvmSupported(JavaVersion javaVersion) {
        return worksWith(javaVersion);
    }

    @Override
    public boolean isToolingApiLocksBuildActionClasses() {
        return isSameOrOlder("3.0");
    }

    @Override
    public boolean isToolingApiLoggingInEmbeddedModeSupported() {
        return isSameOrNewer("2.9-rc-1");
    }

    @Override
    public CacheVersion getArtifactCacheLayoutVersion() {
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
    public boolean wrapperCanExecute(GradleVersion version) {
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
    public boolean isWrapperSupportsGradleUserHomeCommandLineOption() {
        return isSameOrNewer("1.7");
    }

    @Override
    public boolean isSupportsSpacesInGradleAndJavaOpts() {
        return isSameOrNewer("1.0-milestone-5");
    }

    @Override
    public boolean isFullySupportsIvyRepository() {
        return isSameOrNewer("1.0-milestone-7");
    }

    @Override
    public boolean isAddsTaskExecutionExceptionAroundAllTaskFailures() {
        return isSameOrNewer("5.0");
    }

    @Override
    public boolean isToolingApiRetainsOriginalFailureOnCancel() {
        // Versions before 5.1 would unpack the exception and throw part of it, losing some context
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    public boolean isToolingApiDoesNotAddCausesOnTaskCancel() {
        // Versions before 5.1 would sometimes add some additional 'build cancelled' exceptions
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    public boolean isToolingApiHasCauseOnCancel() {
        // Versions before 3.2 would throw away the cause. There was also a regression in 4.0.x
        return isSameOrNewer("3.2") && !(isSameOrNewer("4.0") && isSameOrOlder("4.0.2"));
    }

    @Override
    public boolean isToolingApiHasCauseOnForcedCancel() {
        // Versions before 5.1 would discard context on forced cancel
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    public boolean isToolingApiLogsFailureOnCancel() {
        // Versions before 4.1 would log "CONFIGURE SUCCESSFUL" for model/action execution (but "BUILD FAILED" for task/test execution)
        return isSameOrNewer("4.1");
    }

    @Override
    public boolean isToolingApiHasCauseOnPhasedActionFail() {
        return isSameOrNewer("5.1-rc-1");
    }

    @Override
    public boolean isToolingApiMergesStderrIntoStdout() {
        return isSameOrNewer("4.7") && isSameOrOlder("5.0");
    }

    @Override
    public boolean isToolingApiLogsConfigureSummary() {
        return isSameOrNewer("2.14");
    }

    @Override
    public <T> T selectOutputWithFailureLogging(T stdout, T stderr) {
        if (isSameOrNewer("4.0") && isSameOrOlder("4.6") || isSameOrNewer("5.1-rc-1")) {
            return stderr;
        }
        return stdout;
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
