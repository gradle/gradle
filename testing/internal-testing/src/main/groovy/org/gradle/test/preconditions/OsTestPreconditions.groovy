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
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.TestPrecondition

/**
 * Preconditions for operating system detection.
 * Checks which OS the test is running on, including OS/JDK combination checks.
 *
 * @see org.gradle.test.precondition
 */
@CompileStatic
class OsTestPreconditions {

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

    static final class NotAlpine implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return System.getenv("RUNNING_ON_ALPINE") == null
        }
    }

    static final class NotWindowsJavaBefore11 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(Windows) || TestPrecondition.satisfied(JdkVersionTestPreconditions.Jdk11OrLater)
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
}
