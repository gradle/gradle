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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.TestPrecondition

/**
 * Preconditions for JDK/JRE installations available on disk.
 * Checks whether specific JDK versions are installed on this machine, whether multiple JDKs
 * are available, and whether JDKs meeting daemon compatibility requirements exist.
 * Unlike {@link JdkVersionTestPreconditions}, which checks the running JVM, this class
 * checks what is installed on the filesystem via {@link AvailableJavaHomes}.
 *
 * @see org.gradle.test.precondition
 */
class InstalledJdkTestPreconditions {

    static class Java7HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(7)
            )
        }
    }

    static class Java8HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(8)
            )
        }
    }

    static class Java11HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(11)
            )
        }
    }

    static class Java17HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(17)
            )
        }
    }

    static class Java18HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(18)
            )
        }
    }

    static class Java19HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(19)
            )
        }
    }

    static class Java20HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(20)
            )
        }
    }

    static class Java21HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(21)
            )
        }
    }

    static class Java22HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(22)
            )
        }
    }

    static class Java23HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(23)
            )
        }
    }

    static class Java24HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(24)
            )
        }
    }

    static class MoreThanOneJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.availableJvms.size() >= 2
        }
    }

    static class MoreThanOneJava8HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getAvailableJdks(
                JavaVersion.toVersion(8)
            ).size() > 1
        }
    }

    static class BestJreAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.bestJre != null
        }
    }

    static final class JavaRuntimeVersionSystemPropertyAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return System.getProperty('java.runtime.version') != null
        }
    }

    static class JavaHomeWithDifferentVersionAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.differentVersionAvailable;
        }
    }

    static class JavaHomeWithTwoDifferentVersionsAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            def firstDifferent = AvailableJavaHomes.differentVersionOrNull
            return firstDifferent != null && AvailableJavaHomes.getDifferentVersion(firstDifferent.javaVersion) != null
        }
    }

    static class DifferentJdksFromMultipleVendors implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getAvailableJvmMetadatas().stream()
                .filter { metadata -> !AvailableJavaHomes.isCurrentJavaHome(metadata) }
                .map {metadata -> metadata.vendor.rawVendor }
                .distinct()
                .count() >= 2
        }
    }

    static class DifferentJdkAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.isDifferentJdkAvailable();
        }
    }

    /**
     * A JVM that is not able to run a Gradle worker is available.
     */
    static class UnsupportedWorkerJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !AvailableJavaHomes.unsupportedWorkerJdks.isEmpty()
        }
    }

    /**
     * A JVM that is not able to run the Gradle daemon is available.
     */
    static class UnsupportedDaemonJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !AvailableJavaHomes.unsupportedDaemonJdks.isEmpty()
        }
    }

    /**
     * A JVM that can run the Gradle daemon, but will not be able to in the next major version, is available.
     */
    static class DeprecatedDaemonJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.deprecatedDaemonJdk != null
        }
    }

    /**
     * A JVM that can run the Gradle daemon, and will continue to be able to in the next major version, is available.
     */
    static class NonDeprecatedDaemonJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.nonDeprecatedDaemonJdk != null
        }
    }
}
