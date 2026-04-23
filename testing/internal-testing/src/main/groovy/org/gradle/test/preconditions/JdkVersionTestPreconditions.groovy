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
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.test.precondition.TestPrecondition
import org.jetbrains.kotlin.config.JvmTarget

/**
 * Preconditions for the currently running JVM's version and vendor.
 * Checks the JVM that is executing the tests, not what JDKs are installed on disk
 * (see {@link InstalledJdkTestPreconditions} for that).
 *
 * @see org.gradle.test.precondition
 */
@CompileStatic
class JdkVersionTestPreconditions {

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
}
