/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.preconditions

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.TestPrecondition
import org.jetbrains.kotlin.config.JvmTarget
import org.testcontainers.DockerClientFactory

@CompileStatic
class UnitTestPreconditions {

    static final class Symlinks implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(MacOs) || TestPrecondition.satisfied(Linux)
        }
    }

    static final class NoSymlinks implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(Symlinks)
        }
    }

    static final class CaseInsensitiveFs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(MacOs) || TestPrecondition.satisfied(Windows)
        }
    }

    static final class CaseSensitiveFs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(CaseInsensitiveFs)
        }
    }

    static final class FilePermissions implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(MacOs) || TestPrecondition.satisfied(Linux)
        }
    }

    static final class NoFilePermissions implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(FilePermissions)
        }
    }

    static final class WorkingDir implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() < JavaVersion.VERSION_11
        }
    }

    static final class MandatoryFileLockOnOpen implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(Windows)
        }
    }

    static final class NoMandatoryFileLockOnOpen implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(MandatoryFileLockOnOpen)
        }
    }

    static final class Windows implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return OperatingSystem.current().isWindows()
        }
    }

    static final class NotWindows implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(Windows)
        }
    }

    static final class NotAlpine implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return System.getenv("RUNNING_ON_ALPINE") == null
        }
    }

    static final class NotWindowsJavaBefore11 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(Windows) || TestPrecondition.satisfied(Jdk11OrLater)
        }
    }

    /**
     * @see <a href="https://github.com/gradle/gradle/issues/1111">Link</a>
     */
    static final class IsKnownWindowsSocketDisappearanceIssue implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return Jvm.current().javaVersionMajor >= 7 &&
                Jvm.current().javaVersionMajor <= 8 &&
                OperatingSystem.current().isWindows()
        }
    }

    /**
     * @see <a href="https://github.com/gradle/gradle/issues/1111">Link</a>
     */
    static final class IsNotKnownWindowsSocketDisappearanceIssue implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(IsKnownWindowsSocketDisappearanceIssue)
        }
    }

    static final class MacOs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return OperatingSystem.current().isMacOsX()
        }
    }

    static final class NotMacOs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(MacOs)
        }
    }

    static final class MacOsM1 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(MacOs) && OperatingSystem.current().toString().contains("aarch64")
        }
    }

    static final class NotMacOsM1 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(MacOsM1)
        }
    }

    static final class Linux implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return OperatingSystem.current().linux
        }
    }

    static final class NotLinux implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(Linux)
        }
    }

    static final class Unix implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return OperatingSystem.current().isUnix()
        }
    }

    static final class HasDocker implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            try {
                DockerClientFactory.instance().client()
            } catch (Exception ex) {
                return false
            }
            // https://github.com/gradle/gradle-private/issues/4580
            return false
        }
    }

    /**
     * The current JVM is not able to run the Gradle daemon.
     */
    static final class UnsupportedDaemonJdkVersion implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            def currentMajor = Integer.parseInt(JavaVersion.current().majorVersion)
            return currentMajor < SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION
        }
    }

    /**
     * The current JVM can run the Gradle daemon, but will not be able to in the next major version.
     */
    static final class DeprecatedDaemonJdkVersion implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            def currentMajor = Integer.parseInt(JavaVersion.current().majorVersion)
            return (currentMajor < SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION) &&
                (currentMajor >= SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION)
        }
    }

    /**
     * The current JVM can run the Gradle daemon, and will continue to be able to in the next major version.
     */
    static final class NonDeprecatedDaemonJdkVersion implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            def currentMajor = Integer.parseInt(JavaVersion.current().majorVersion)
            return currentMajor >= SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION
        }
    }

    static final class Jdk8OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_1_8
        }
    }

    static final class Jdk9OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_1_9
        }
    }

    static final class Jdk9OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_1_9
        }
    }

    static final class Jdk10OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_1_10
        }
    }

    static final class Jdk11OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_11
        }
    }

    static final class Jdk11OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_11
        }
    }

    static final class Jdk12OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_12
        }
    }

    static final class Jdk13OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_13
        }
    }

    static final class Jdk14OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_14
        }
    }

    static final class Jdk15OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_15
        }
    }

    static final class Jdk16OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_16
        }
    }

    static final class Jdk17OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_17
        }
    }

    static final class Jdk19OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_19
        }
    }

    static final class Jdk21OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_21
        }
    }

    static final class Jdk21OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_21
        }
    }

    static final class Jdk23OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_23
        }
    }

    static final class Jdk24OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_24
        }
    }

    static final class Jdk24OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_24
        }
    }

    static final class JdkOracle implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            System.getProperty('java.vm.vendor') == 'Oracle Corporation'
        }
    }

    static final class KotlinSupportedJdk implements TestPrecondition {

        private static final JavaVersion MAX_SUPPORTED_JAVA_VERSION =
            JavaVersion.forClassVersion(
                JvmTarget.values().max { it.majorVersion }.majorVersion
            )

        @Override
        boolean isSatisfied() throws Exception {
            return JavaVersion.current() <= MAX_SUPPORTED_JAVA_VERSION
        }
    }

    static final class Online implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("http://google.com").openConnection();
                connection.setConnectTimeout(60 * 1000);
                connection.setReadTimeout(60 * 1000);
                connection.getInputStream().close();
                return true
            } catch (IOException ex) {
                return false
            }
        }
    }

    static final class CanInstallExecutable implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(FilePermissions) || TestPrecondition.satisfied(Windows)
        }
    }

    static final class SmartTerminalAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return System.getenv("TERM")?.toUpperCase() != "DUMB"
        }
    }

    static final class HasXCode implements TestPrecondition {
        private static Boolean installed = null

        private static boolean isInstalled() {
            if (OperatingSystem.current().isMacOsX()) {
                // XCTest is bundled with XCode, so the test cannot be run if XCode is not installed
                def result = ["xcrun", "--show-sdk-platform-path"].execute().waitFor()
                // If it fails, assume XCode is not installed
                return result == 0
            } else {
                return false
            }
        }

        @Override
        boolean isSatisfied() {
            if (installed == null) {
                installed = isInstalled()
            }
            return installed
        }
    }

    static final class HasXCTest implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            // Bundled with XCode on macOS
            return TestPrecondition.notSatisfied(MacOs) || TestPrecondition.satisfied(HasXCode)
        }
    }

    // Currently mac agents are not that strong so we avoid running high-concurrency tests on them
    static final class HighPerformance implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(MacOs)
        }
    }

    static final class NotEC2Agent implements TestPrecondition {
        @Override
        boolean isSatisfied() throws UnknownHostException {
            return !InetAddress.getLocalHost().getHostName().startsWith("ip-")
        }
    }

    static final class StableGroovy implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            !GroovySystem.version.endsWith("-SNAPSHOT")
        }
    }

    static final class NotStableGroovy implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            TestPrecondition.notSatisfied(StableGroovy)
        }
    }

    static final class OnRemoteTestDistributionExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return System.getenv("RUNNING_ON_REMOTE_AGENT") != null
        }
    }

    static final class NotInGradleceptionBuild implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return System.getenv("BUILD_TYPE_ID")?.contains("Check_Gradleception") != true
        }
    }
}
