/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal

import org.gradle.internal.SystemProperties
import org.gradle.util.GFileUtils
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class TemporaryGradleRunnerWorkingSpaceDirectoryProviderTest extends Specification {
    @Rule TemporaryFolder tmpParentDir = new TemporaryFolder()
    File expectedTmpDir
    GradleRunnerWorkingSpaceDirectoryProvider temporaryGradleRunnerWorkingSpaceDirectoryProvider

    def setup() {
        temporaryGradleRunnerWorkingSpaceDirectoryProvider = new TemporaryGradleRunnerWorkingSpaceDirectoryProvider(tmpParentDir.root)
        expectedTmpDir = new File(tmpParentDir.root, ".gradle-test-kit-${SystemProperties.instance.userName}")
    }

    def "can create temporary directory"() {
        when:
        File tmpDir = temporaryGradleRunnerWorkingSpaceDirectoryProvider.createDir()

        then:
        tmpDir.exists()
        tmpDir == expectedTmpDir
    }

    def "existing temporary directory isn't deleted"() {
        when:
        File tmpDir = temporaryGradleRunnerWorkingSpaceDirectoryProvider.createDir()

        then:
        tmpDir.exists()
        tmpDir == expectedTmpDir

        when:
        File tmpFile = new File(tmpDir, 'test.txt')
        GFileUtils.touch(tmpFile)
        File existingTmpDir = temporaryGradleRunnerWorkingSpaceDirectoryProvider.createDir()

        then:
        tmpDir == existingTmpDir
        tmpFile.exists()
    }
}
