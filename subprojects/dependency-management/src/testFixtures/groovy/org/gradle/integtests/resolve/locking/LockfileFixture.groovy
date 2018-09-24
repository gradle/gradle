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

    def createLockfile(String configurationName, List<String> modules) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        def lines = [LockFileReaderWriter.LOCKFILE_HEADER]
        lines.addAll modules
        lockFile.writelns(lines.sort())
    }

    void verifyLockfile(String configurationName, List<String> expectedModules) {
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

    void expectMissing(String configurationName) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        assert !lockFile.exists()
    }
}
