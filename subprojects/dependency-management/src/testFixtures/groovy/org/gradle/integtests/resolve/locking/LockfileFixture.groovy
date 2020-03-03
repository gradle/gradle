/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.locking

import org.gradle.internal.locking.LockFileReaderWriter
import org.gradle.test.fixtures.file.TestFile

class LockfileFixture {

    TestFile testDirectory

    def createLockfile(List<String> entries, String empty = '') {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)
        def lines = [LockFileReaderWriter.LOCKFILE_HEADER]
        lines.addAll entries.sort()
        lines.add 'empty=' + empty
        lockFile.writelns(lines)
    }

    void verifyLockfile(List<String> entries, String empty = '') {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)
        assert lockFile.exists()
        def lockedModules = []
        lockFile.eachLine { String line ->
            if (!line.startsWith('#')) {
                lockedModules << line
            }
        }

        List<String> expectedModules = new ArrayList<>()
        expectedModules.addAll(entries)
        expectedModules.sort()
        expectedModules.add('empty=' + empty)

        assert lockedModules == expectedModules
    }

    def createLegacyLockfile(String configurationName, List<String> modules) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        def lines = [LockFileReaderWriter.LOCKFILE_HEADER]
        lines.addAll modules
        lockFile.writelns(lines.sort())
    }

    void verifyLegacyLockfile(String configurationName, List<String> expectedModules) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        assert lockFile.exists()
        def lockedModules = []
        lockFile.eachLine { String line ->
            if (!line.startsWith('#')) {
                lockedModules << line
            }
        }

        assert lockedModules as Set == expectedModules as Set
    }

    void expectLegacyMissing(String configurationName) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        assert !lockFile.exists()
    }
}
