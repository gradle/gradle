/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.tasks

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE
import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.ensureDirectoryForProperty

@CleanupTestDirectory
class TaskOutputPackerUtilsTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def "parent directory is created for output file"() {
        def targetOutputFile = temporaryFolder.file("build/some-dir/output.txt")
        targetOutputFile << "Some data"

        when:
        ensureDirectoryForProperty(FILE, targetOutputFile)

        then:
        targetOutputFile.parentFile.assertIsEmptyDir()
    }

    def "directory is created for output directory"() {
        def targetOutputDir = temporaryFolder.file("build/output")

        when:
        ensureDirectoryForProperty(DIRECTORY, targetOutputDir)

        then:
        targetOutputDir.assertIsEmptyDir()
    }

    def "cleans up leftover files in output directory"() {
        def targetOutputDir = temporaryFolder.file("build/output")
        targetOutputDir.file("sub-dir/data.txt") << "Some data"

        when:
        ensureDirectoryForProperty(DIRECTORY, targetOutputDir)

        then:
        targetOutputDir.assertIsEmptyDir()
    }

    def "creates directories even if there is a pre-existing file in its place"() {
        def targetOutputDir = temporaryFolder.file("build/output")
        targetOutputDir << "This should become a directory"

        when:
        ensureDirectoryForProperty(DIRECTORY, targetOutputDir)

        then:
        targetOutputDir.assertIsEmptyDir()
    }

    def "creates parent directories for output file even if there is a pre-existing directory in its place"() {
        def targetOutputFile = temporaryFolder.file("build/some-dir/output.txt")
        targetOutputFile.createDir()

        when:
        ensureDirectoryForProperty(FILE, targetOutputFile)

        then:
        targetOutputFile.parentFile.assertIsEmptyDir()
    }

}
