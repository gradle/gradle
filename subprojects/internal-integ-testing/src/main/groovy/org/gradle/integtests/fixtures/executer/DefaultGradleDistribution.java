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
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;
import org.gradle.util.VersionNumber;

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

    public TestFile getGradleHomeDir() {
        return gradleHomeDir;
    }

    public TestFile getBinDistribution() {
        return binDistribution;
    }

    public GradleVersion getVersion() {
        return version;
    }

    public GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        return new ForkingGradleExecuter(this, testDirectoryProvider, version, buildContext);
    }

    public boolean worksWith(Jvm jvm) {
        // Milestone 4 was broken on the IBM jvm
        if (jvm.isIbmJvm() && version.isVersion("1.0-milestone-4")) {
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
        if (version.isVersion("0.9-rc-1") && javaVersion == JavaVersion.VERSION_1_5) {
            return false;
        }

        // 1.x works on Java 5 - 8
        if (version.isSameOrOlder("1.12")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_5) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        // 2.x and 3.0-milestone-1 work on Java 6 - 8
        if (version.isSameOrOlder("3.0-milestone-1")) {
            return javaVersion.compareTo(JavaVersion.VERSION_1_6) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_8) <= 0;
        }

        // 3.x works on Java 7 - 9
        return javaVersion.compareTo(JavaVersion.VERSION_1_7) >= 0 && javaVersion.compareTo(JavaVersion.VERSION_1_9) <= 0;
    }

    public boolean worksWith(OperatingSystem os) {
        // 1.0-milestone-5 was broken where jna was not available
        //noinspection SimplifiableIfStatement
        if (version.isVersion("1.0-milestone-5")) {
            return os.isWindows() || os.isMacOsX() || os.isLinux();
        } else {
            return true;
        }
    }

    public boolean isDaemonIdleTimeoutConfigurable() {
        return version.isSameOrNewer("1.0-milestone-7");
    }

    public boolean isOpenApiSupported() {
        return version.isSameOrNewer("0.9-rc-1") && !version.isSameOrNewer("2.0-rc-1");
    }

    public boolean isToolingApiSupported() {
        return version.isSameOrNewer("1.0-milestone-3");
    }

    @Override
    public boolean isToolingApiTargetJvmSupported(JavaVersion javaVersion) {
        return worksWith(javaVersion);
    }

    public boolean isToolingApiNonAsciiOutputSupported() {
        if (OperatingSystem.current().isWindows()) {
            return !version.isVersion("1.0-milestone-7") && !version.isVersion("1.0-milestone-8") && !version.isVersion("1.0-milestone-8a");
        }
        return true;
    }

    public boolean isToolingApiDaemonBaseDirSupported() {
        return version.isSameOrNewer("2.2-rc-1");
    }

    @Override
    public boolean isToolingApiEventsInEmbeddedModeSupported() {
        return version.isSameOrNewer("2.6-rc-1");
    }

    @Override
    public boolean isToolingApiLocksBuildActionClasses() {
        return version.isSameOrOlder("3.0");
    }

    @Override
    public boolean isToolingApiLoggingInEmbeddedModeSupported() {
        return version.isSameOrNewer("2.9-rc-1");
    }

    public VersionNumber getArtifactCacheLayoutVersion() {
        if (version.isSameOrNewer("3.2-rc-1")) {
            return VersionNumber.parse("2.23");
        } else if (version.isSameOrNewer("3.1-rc-1")) {
            return VersionNumber.parse("2.21");
        } else if (version.isSameOrNewer("3.0-milestone-1")) {
            return VersionNumber.parse("2.17");
        } else if (version.isSameOrNewer("2.8-rc-1")) {
            return VersionNumber.parse("2.16");
        } else if (version.isSameOrNewer("2.4-rc-1")) {
            return VersionNumber.parse("2.15");
        } else if (version.isSameOrNewer("2.2-rc-1")) {
            return VersionNumber.parse("2.14");
        } else if (version.isSameOrNewer("2.1-rc-3")) {
            return VersionNumber.parse("2.13");
        } else if (version.isSameOrNewer("2.0-rc-1")) {
            return VersionNumber.parse("2.12");
        } else if (version.isSameOrNewer("1.12-rc-1")) {
            return VersionNumber.parse("2.6");
        } else if (version.isSameOrNewer("1.11-rc-1")) {
            return VersionNumber.parse("2.2");
        } else if (version.isSameOrNewer("1.9-rc-2")) {
            return VersionNumber.parse("2.1");
        } else if (version.isSameOrNewer("1.9-rc-1")) {
            return VersionNumber.parse("1.31");
        } else if (version.isSameOrNewer("1.7-rc-1")) {
            return VersionNumber.parse("0.26");
        } else if (version.isSameOrNewer("1.6-rc-1")) {
            return VersionNumber.parse("0.24");
        } else if (version.isSameOrNewer("1.4-rc-1")) {
            return VersionNumber.parse("0.23");
        } else if (version.isSameOrNewer("1.3")) {
            return VersionNumber.parse("0.15");
        } else {
            return VersionNumber.parse("0.1");
        }
    }

    public boolean wrapperCanExecute(GradleVersion version) {
        if (version.equals(GradleVersion.version("0.8")) || version.isVersion("0.8")) {
            // There was a breaking change after 0.8
            return false;
        }
        if (version.isVersion("0.9.1")) {
            // 0.9.1 couldn't handle anything with a timestamp whose timezone was behind GMT
            return version.getVersion().matches(".*+\\d{4}");
        }
        if (version.isSameOrNewer("0.9.2") && version.isSameOrOlder("1.0-milestone-2")) {
            // These versions couldn't handle milestone patches
            if (version.getVersion().matches("1.0-milestone-\\d+[a-z]-.+")) {
                return false;
            }
        }
        return true;
    }

    public boolean isWrapperSupportsGradleUserHomeCommandLineOption() {
        return version.isSameOrNewer("1.7");
    }

    public boolean isSupportsSpacesInGradleAndJavaOpts() {
        return version.isSameOrNewer("1.0-milestone-5");
    }

    public boolean isFullySupportsIvyRepository() {
        return version.isSameOrNewer("1.0-milestone-7");
    }

    public boolean isLifecycleLogLevelFlagSupported() {
        return version.isSameOrNewer("4.0");
    }

}
