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

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.ResourceBasedJvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.source.ClasspathResourceSource
import org.gradle.tooling.events.test.source.CompositeTestSource
import org.gradle.tooling.events.test.source.DirectorySource
import org.gradle.tooling.events.test.source.FileSource
import org.gradle.tooling.events.test.source.MissingSource
import org.gradle.tooling.events.test.source.PackageSource
import org.gradle.tooling.events.test.source.UnknownSource
import testengines.TestEnginesFixture

@TargetGradleVersion(">=9.4.0")
@ToolingApiVersion(">=9.4.0")
class TestOperationDescriptorTestSourceCrossVersionTest extends AbstractResourceBasedTestingCrossVersionTest {

    public static final DEFAULT_DEFINITIONS_LOCATION = "src/test/definitions"

    ProgressEvents events = ProgressEvents.create()
    @Override
    List<TestEnginesFixture.TestEngines> getEnginesToSetup() {
        return [TestEnginesFixture.TestEngines.CUSTOM_SOURCE_ENGINE]
    }

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
            it.newBuild().addProgressListener(events, OperationType.TEST).forTasks("test").run()
        }

        then:
        events.tests.size() == 3 // task + executor + 1 tests
        def testOperation = events.operation("Test SomeTestSpec.rbt : $testName")
        testOperation.assertIsTest()
        def descriptor = testOperation.descriptor as TestOperationDescriptor
        testSourceType.isAssignableFrom(descriptor.testSource.class)
        validateTestSource(descriptor.testSource)
        if (resourceBased) {
            assert descriptor instanceof ResourceBasedJvmTestOperationDescriptor
        } else {
            assert descriptor instanceof JvmTestOperationDescriptor
        }

        where:
        testName                            | testSourceType          | resourceBased | validateTestSource
        'noLocation'                        | MissingSource           | false         | { true }
        'unknownLocation'                   | UnknownSource           | false         | { true }
        'fileLocationNoPos'                 | FileSource              | true          | { FileSource s -> s.position == null }
        'fileLocationOnlyLine'              | FileSource              | true          | { FileSource s -> s.position.line == 1 && s.position.column == null }
        'fileLocationLineAndCol'            | FileSource              | true          | { FileSource s -> s.position.line == 1 && s.position.column == 2 }
        'directorySource'                   | DirectorySource         | true          | { DirectorySource s -> s.file.isDirectory() }
        'classpathResourceSourceNoPos'      | ClasspathResourceSource | false         | { ClasspathResourceSource s -> s.classpathResourceName == "SomeClass" && s.position == null }
        'classpathResourceSourceOnlyLine'   | ClasspathResourceSource | false         | { ClasspathResourceSource s -> s.classpathResourceName == "SomeClass" && s.position.line == 1 && s.position.column == null }
        'classpathResourceSourceLineAndCol' | ClasspathResourceSource | false         | { ClasspathResourceSource s -> s.classpathResourceName == "SomeClass" && s.position.line == 1 && s.position.column == 2 }
        'packageLocation'                   | PackageSource           | false         | { PackageSource s -> s.packageName == "some.package" }
        'unknownAndFileLocation'            | CompositeTestSource     | false         | { CompositeTestSource s -> s.testSources[0] instanceof UnknownSource && s.testSources[1] instanceof FileSource }
    }
}
