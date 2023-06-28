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

package org.gradle.configurationcache.inputs.undeclared

import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.test.fixtures.file.TestFile

class UndeclaredBuildInputsIgnoringIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def 'can ignore a file system check configuration inputs #location'() {
        given:
        def inputFile = fileClosure.call(testDirectory)
        buildFile("""
            println("exists = " + new File("${inputFile.absolutePath}").exists())
        """)

        when:
        configurationCacheRun()

        then:
        outputContains("exists = false")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': file system entry")
        }

        when:
        file("gradle.properties") << """$IGNORE_FS_CHECKS_PROPERTY=$pattern"""
        configurationCacheRun()

        then:
        outputContains("exists = false")
        problems.assertResultHasProblems(result) {
            withNoInputs()
        }

        where:
        location                        | pattern            | fileClosure
        "inside the project directory"  | "build/*.lock"     | { TestFile testDir -> testDir.file("build/test.lock").absoluteFile }
        "outside the project directory" | "../../*.lock"     | { TestFile testDir -> testDir.file("../../test.lock").absoluteFile }
        "in user home directory"        | "~/.gradle/*.lock" | { TestFile testDir -> new File(System.getProperty("user.home"), ".gradle/test.lock") }
        "by absolute path"              | "/test/*.lock"     | { TestFile testDir -> new File("/test/test.lock") }
    }

    def 'can ignore file system checks in multiple paths if separated by semicolon'() {
        given:
        buildFile("""
            println("exists = " + new File(projectDir, "file1.txt").exists())
            println("exists = " + new File(projectDir, "file2.txt").exists())
            println("exists = " + new File(projectDir, "file3.txt").exists())
        """)

        when:
        file("gradle.properties") << """
            $IGNORE_FS_CHECKS_PROPERTY=file1.txt;file2.txt
        """
        configurationCacheRun()

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': file system entry 'file3.txt'")
        }
    }

    def 'paths ignored in file system checks are included in the configuration cache fingerprint'() {
        when:
        configurationCacheRun()
        configurationCacheRun("-D$IGNORE_FS_CHECKS_PROPERTY=test")

        then:
        outputContains("the set of paths ignored in file-system-check input tracking has changed")
    }

    private static final String IGNORE_FS_CHECKS_PROPERTY = StartParameterBuildOptions.ConfigurationCacheIgnoredFileSystemCheckInputs.PROPERTY_NAME
}
