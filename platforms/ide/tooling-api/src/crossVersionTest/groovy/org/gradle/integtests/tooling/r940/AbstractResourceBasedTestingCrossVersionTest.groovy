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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.tooling.TestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import testengines.TestEnginesFixture

abstract class AbstractResourceBasedTestingCrossVersionTest extends ToolingApiSpecification implements TestEventsFixture {

    protected static final DEFAULT_DEFINITIONS_LOCATION = "src/test/definitions"
    private static File engineJarLib

    ProgressEvents events = ProgressEvents.create()

    abstract List<TestEnginesFixture.TestEngines> getEnginesToSetup()

    def setup() {
        TestDirectoryProvider testClassDirectoryProvider = new TestNameTestDirectoryProvider.UniquePerTestClassDirectoryProvider(this.getClass())
        TestResources resources = new TestResources(testClassDirectoryProvider, TestEnginesFixture.TestEngines.class, this.getClass())

        String engineCopyToDirName = "test-engine-build"
        if (testClassDirectoryProvider.testDirectory.file(engineCopyToDirName)) {
            testClassDirectoryProvider.testDirectory.file(engineCopyToDirName).deleteDir()
        }

        resources.maybeCopy("shared")
        enginesToSetup.forEach {
            resources.maybeCopy(it.name)
        }

        File engineBuildDir = testClassDirectoryProvider.testDirectory.file(engineCopyToDirName)
        withConnection(connector().forProjectDirectory(engineBuildDir)) {
            it.newBuild().forTasks("build").run()
        }
        engineJarLib = engineBuildDir.file("build/libs/${engineCopyToDirName}.jar")
    }

    protected String enableEngineForSuite() {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${TextUtil.normaliseFileSeparators(engineJarLib.absolutePath)}")
                }
        """
    }

    protected void writeTestDefinitions(String path = "src/test/definitions") {
        file("$path/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
                <test name="bar" />
            </tests>
        """
        file("$path/subSomeOtherTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="other" />
            </tests>
        """
    }

    protected void assertTestsFromAllDefinitionsExecuted() {
        testEvents {
            task(':test') {
                nested('Gradle Test Run :test') {
                    nested('Gradle Test Executor') {
                        test('Test SomeTestSpec.rbt - foo')
                        test('Test SomeTestSpec.rbt - bar')
                        test('Test subSomeOtherTestSpec.rbt - other')
                    }
                }
            }
        }
    }

    protected void assertTestsFromSecondDefinitionsExecuted() {
        testEvents {
            task(':test') {
                nested('Gradle Test Run :test') {
                    nested('Gradle Test Executor') {
                        test('Test subSomeOtherTestSpec.rbt - other')
                    }
                }
            }
        }
    }
}
