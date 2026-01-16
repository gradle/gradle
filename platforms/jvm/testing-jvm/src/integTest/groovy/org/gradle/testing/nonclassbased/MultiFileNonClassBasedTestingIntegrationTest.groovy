/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.nonclassbased

/**
 * Tests that exercise and demonstrate Non-Class-Based Testing using the {@code Test} task
 * and a sample resource-based JUnit Platform Test Engine that defines tests across multiple files
 * in a directory.
 */
class MultiFileNonClassBasedTestingIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.MULTI_FILE_RESOURCE_BASED]
    }

    def "resource-based test engine detects and executes test definitions"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$DEFAULT_DEFINITIONS_LOCATION")
                    }
                }
            }
        """

        ["test1", "test2"].each { dirName ->
            def testDir = file("$DEFAULT_DEFINITIONS_LOCATION/$dirName")
            testDir.mkdirs()
            new File(testDir, "first-half.txt").createNewFile()
            new File(testDir, "second-half.txt").createNewFile()
        }

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(":first-half.txt - test1", ":first-half.txt - test2",
            ":second-half.txt - test1", ":second-half.txt - test2")
    }
}
