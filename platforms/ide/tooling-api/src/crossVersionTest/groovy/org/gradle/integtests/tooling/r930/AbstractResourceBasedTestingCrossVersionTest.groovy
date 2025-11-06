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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification

abstract class AbstractResourceBasedTestingCrossVersionTest extends ToolingApiSpecification {

    private static final String ENGINE_COPY_TO_DIR_NAME = "test-engine-build"
    String engineJarLibPath

    abstract List<TestEngines> getEnginesToSetup()

    def setup() {
        // build junit test engine from sources defined in the testing-jvm subprojects
        buildContext.testFile("src/testFixtures/resources/testEngines/shared").copyTo(file())
        getEnginesToSetup().forEach {
            buildContext.testFile("src/testFixtures/resources/testEngines/${it.name}").copyTo(file())
        }
        withConnection(connector().forProjectDirectory(file(ENGINE_COPY_TO_DIR_NAME))) {
            it.newBuild().forTasks("build").run()
        }
        engineJarLibPath = file("test-engine-build/build/libs/${ENGINE_COPY_TO_DIR_NAME}.jar").absolutePath
    }

    protected String enableEngineForSuite() {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${TextUtil.normaliseFileSeparators(engineJarLibPath)}")
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

    enum TestEngines {
        BASIC_RESOURCE_BASED("rbt-engine"),
        MULTI_FILE_RESOURCE_BASED("multi-file-rbt-engine"),
        RESOURCE_AND_CLASS_BASED("resource-and-class-engine"),
        FAILS_DISCOVERY_RESOURCE_BASED("fails-discovery-rbt-engine"),
        FAILS_EXECUTION_RESOURCE_BASED("fails-execution-rbt-engine"),

        private final String name

        TestEngines(final String name) {
            this.name = name
        }

        String getName() {
            return name
        }
    }
}
