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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.source.ClasspathResourceSource
import org.gradle.tooling.events.test.source.DirectorySource
import org.gradle.tooling.events.test.source.FileSource
import org.gradle.tooling.events.test.source.NoSource
import org.gradle.tooling.events.test.source.OtherSource

@TargetGradleVersion(">=9.4.0")
@ToolingApiVersion(">=9.4.0")
class TestOperationDescriptorTestSourceCrossVersionTest extends AbstractResourceBasedTestingCrossVersionTest {

    def "receives custom test sources from custom test engine"() {
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

        file("src/test/definitions/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="$testName" />
            </tests>
        """

        when:
        withConnection {
             it.newBuild().addProgressListener(events, OperationType.TASK, OperationType.TEST).forTasks("test").run()
        }

        then:
        testEvents {
            task(':test') {
                nested('Gradle Test Run :test') {
                    nested('Gradle Test Executor') {
                        test("Test SomeTestSpec.rbt - $testName")
                    }
                }
            }
        }
        def testOperation = events.operation("Test SomeTestSpec.rbt - $testName")
        def descriptor = testOperation.descriptor as JvmTestOperationDescriptor
        testSourceType.isAssignableFrom(descriptor.source.class)
        validateTestSource(descriptor.source)

        where:
        testName                            | testSourceType          | validateTestSource
        'noLocation'                        | NoSource                | { true }
        'unknownLocation'                   | OtherSource             | { true }
        'fileLocationNoPos'                 | FileSource              | { FileSource s -> s.position == null }
        'fileLocationOnlyLine'              | FileSource              | { FileSource s -> s.position.line == 1 && s.position.column == null }
        'fileLocationLineAndCol'            | FileSource              | { FileSource s -> s.position.line == 1 && s.position.column == 2 }
        'directorySource'                   | DirectorySource         | { DirectorySource s -> s.file.isDirectory() }
        'classpathResourceSourceNoPos'      | ClasspathResourceSource | { ClasspathResourceSource s -> s.classpathResourceName == "SomeClass" && s.position == null }
        'classpathResourceSourceOnlyLine'   | ClasspathResourceSource | { ClasspathResourceSource s -> s.classpathResourceName == "SomeClass" && s.position.line == 1 && s.position.column == null }
        'classpathResourceSourceLineAndCol' | ClasspathResourceSource | { ClasspathResourceSource s -> s.classpathResourceName == "SomeClass" && s.position.line == 1 && s.position.column == 2 }
    }
}
