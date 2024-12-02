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
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.TestPrecondition
import org.jetbrains.kotlin.config.JvmTarget
import org.testcontainers.DockerClientFactory

// These imports are required, IntelliJ incorrectly thinks that they are not used because old versions of Groovy
// permitted subtypes to use the parent type's methods without importing them
import static org.gradle.test.precondition.TestPrecondition.satisfied;
import static org.gradle.test.precondition.TestPrecondition.notSatisfied;

@CompileStatic
class UnitTestPreconditions {

    static final class Symlinks implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return satisfied(MacOs) || satisfied(Linux)
        }
    }

    static final class NoSymlinks implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(Symlinks)
        }
    }

    static final class CaseInsensitiveFs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return satisfied(MacOs) || satisfied(Windows)
        }
    }

    static final class CaseSensitiveFs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(CaseInsensitiveFs)
        }
    }

    static final class FilePermissions implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return satisfied(MacOs) || satisfied(Linux)
        }
    }

    static final class NoFilePermissions implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(FilePermissions)
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
            return satisfied(Windows)
        }
    }

    static final class NoMandatoryFileLockOnOpen implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(MandatoryFileLockOnOpen)
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
            return notSatisfied(Windows)
        }
    }

    static final class NotWindowsJavaBefore11 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(Windows) || satisfied(Jdk11OrLater)
        }
    }

    static final class NotWindowsJavaBefore9 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(Windows) || satisfied(Jdk9OrLater)
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
            return notSatisfied(MacOs)
        }
    }

    static final class Java8OnMacOs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return satisfied(MacOs) && JavaVersion.current() == JavaVersion.VERSION_1_8
        }
    }

    static final class NotJava8OnMacOs implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(Java8OnMacOs)
        }
    }

    static final class MacOsM1 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return satisfied(MacOs) && OperatingSystem.current().toString().contains("aarch64")
        }
    }

    static final class NotMacOsM1 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(MacOsM1)
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
            return notSatisfied(Linux)
        }
    }

    static final class Unix implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return OperatingSystem.current().isUnix()
        }
    }

    static final class UnixDerivative implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            satisfied(MacOs) || satisfied(Linux) || satisfied(Unix)
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
            return true
        }
    }

    /**
     * The current JVM is not able to run the Gradle daemon.
     */
    static final class UnsupportedDaemonJdkVersion implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            def currentMajor = Integer.parseInt(JavaVersion.current().majorVersion)
            return currentMajor < SupportedJavaVersions.MINIMUM_JAVA_VERSION
        }
    }

    /**
     * The current JVM can run the Gradle daemon, but will not be able to in the next major version.
     */
    static final class DeprecatedDaemonJdkVersion implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            def currentMajor = Integer.parseInt(JavaVersion.current().majorVersion)
            return (currentMajor < SupportedJavaVersions.FUTURE_MINIMUM_JAVA_VERSION) &&
                (currentMajor >= SupportedJavaVersions.MINIMUM_JAVA_VERSION)
        }
    }

    /**
     * The current JVM can run the Gradle daemon, and will continue to be able to in the next major version.
     */
    static final class NonDeprecatedDaemonJdkVersion implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            def currentMajor = Integer.parseInt(JavaVersion.current().majorVersion)
            return currentMajor >= SupportedJavaVersions.FUTURE_MINIMUM_JAVA_VERSION
        }
    }

    static final class Jdk6OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_1_6
        }
    }

    static final class Jdk6OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_1_6
        }
    }

    static final class Jdk7OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_1_7
        }
    }

    static final class Jdk7OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_1_7
        }
    }

    static final class Jdk8OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_1_8
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

    static final class Jdk10OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_1_10
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

    static final class Jdk12OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_12
        }
    }

    static final class Jdk13OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_13
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

    static final class Jdk14OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_14
        }
    }

    static final class Jdk15OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_15
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

    static final class Jdk16OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_16
        }
    }

    static final class Jdk17OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_17
        }
    }

    static final class Jdk17OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_17
        }
    }

    static final class Jdk18OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_18
        }
    }

    static final class Jdk18OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_18
        }
    }

    static final class Jdk19OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_19
        }
    }

    static final class Jdk19OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_19
        }
    }

    static final class Jdk20OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_20
        }
    }

    static final class Jdk20OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_20
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

    static final class Jdk22OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_22
        }
    }

    static final class Jdk22OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() <= JavaVersion.VERSION_22
        }
    }

    static final class Jdk23OrLater implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() >= JavaVersion.VERSION_23
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
            return satisfied(FilePermissions) || satisfied(Windows)
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
            return notSatisfied(MacOs) || satisfied(HasXCode)
        }
    }

    // Currently mac agents are not that strong so we avoid running high-concurrency tests on them
    static final class HighPerformance implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return notSatisfied(MacOs)
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
            notSatisfied(StableGroovy)
        }
    }

    static final class IsGroovy3 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            GroovySystem.version.startsWith("3.")
        }
    }

    static final class IsGroovy4 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            GroovySystem.version.startsWith("4.")
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
