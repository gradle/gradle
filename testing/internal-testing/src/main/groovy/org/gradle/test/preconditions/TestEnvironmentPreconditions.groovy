/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.TestPrecondition
import org.testcontainers.DockerClientFactory

/**
 * Preconditions for CI infrastructure and external tooling availability.
 * Checks for Docker, network connectivity, XCode, Groovy stability, executable installation support,
 * remote test distribution, and other test environment capabilities.
 *
 * @see org.gradle.test.precondition
 */
@CompileStatic
class TestEnvironmentPreconditions {

    static final class WorkingDir implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return JavaVersion.current() < JavaVersion.VERSION_11
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

    static final class SmartTerminalAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return System.getenv("TERM")?.toUpperCase() != "DUMB"
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
            return TestPrecondition.satisfied(FileSystemTestPreconditions.FilePermissions) || TestPrecondition.satisfied(OsTestPreconditions.Windows)
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
            return TestPrecondition.notSatisfied(OsTestPreconditions.MacOs) || TestPrecondition.satisfied(HasXCode)
        }
    }

    // Currently mac agents are not that strong so we avoid running high-concurrency tests on them
    static final class HighPerformance implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(OsTestPreconditions.MacOs)
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
}
