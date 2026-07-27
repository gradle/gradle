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
import org.gradle.test.precondition.TestPrecondition

/**
 * Preconditions for filesystem capabilities.
 * Checks for symlink support, case sensitivity, file permissions, and mandatory file locking.
 *
 * @see org.gradle.test.precondition
 */
@CompileStatic
class FileSystemTestPreconditions {

    static final class Symlinks implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(OsTestPreconditions.MacOs) || TestPrecondition.satisfied(OsTestPreconditions.Linux)
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
            return TestPrecondition.satisfied(OsTestPreconditions.MacOs) || TestPrecondition.satisfied(OsTestPreconditions.Windows)
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
            return TestPrecondition.satisfied(OsTestPreconditions.MacOs) || TestPrecondition.satisfied(OsTestPreconditions.Linux)
        }
    }

    static final class NoFilePermissions implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(FilePermissions)
        }
    }

    static final class MandatoryFileLockOnOpen implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.satisfied(OsTestPreconditions.Windows)
        }
    }

    static final class NoMandatoryFileLockOnOpen implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            return TestPrecondition.notSatisfied(MandatoryFileLockOnOpen)
        }
    }
}
