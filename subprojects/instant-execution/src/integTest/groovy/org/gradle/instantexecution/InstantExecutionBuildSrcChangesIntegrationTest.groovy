/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.junit.Assume
import spock.lang.Unroll

class InstantExecutionBuildSrcChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    private static final String TASK_NAME = "greet"
    private static final String ORIGINAL_GREETING = "Hello!"
    private static final String CHANGED_GREETING = "G'day!"

    @Unroll
    def "invalidates cache upon change to buildSrc (#buildSrcChange)"() {

        Assume.assumeTrue(
            "wip",
            buildSrcChange != BuildSrcChange.ADD_RESOURCE
        )

        given:
        def instant = newInstantExecutionFixture()
        setUpFor buildSrcChange

        when:
        instantRun()

        then:
        outputContains ORIGINAL_GREETING

        when:
        apply buildSrcChange
        instantRun()

        then:
        outputContains expectedOutputAfterChange
        instant.assertStateStored()

        when:
        instantRun()

        then:
        outputContains expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        buildSrcChange                 | expectedOutputAfterChange
        BuildSrcChange.CHANGE_SOURCE   | CHANGED_GREETING
        BuildSrcChange.ADD_SOURCE      | ORIGINAL_GREETING
        BuildSrcChange.CHANGE_RESOURCE | ORIGINAL_GREETING
        BuildSrcChange.ADD_RESOURCE    | ORIGINAL_GREETING
    }

    private instantRun() {
        instantRun TASK_NAME
    }

    private void setUpFor(BuildSrcChange buildSrcChange) {
        file("buildSrc/build.gradle") << """
            plugins { id("java-library") }
        """
        writeGreetTask "GreetTask", ORIGINAL_GREETING
        buildFile << """
            task $TASK_NAME(type: GreetTask)
        """
        switch (buildSrcChange) {
            case BuildSrcChange.CHANGE_RESOURCE:
                writeBuildSrcResource ""
                break
        }
    }

    private void apply(BuildSrcChange buildSrcChange) {
        switch (buildSrcChange) {
            case BuildSrcChange.CHANGE_SOURCE:
                writeGreetTask "GreetTask", CHANGED_GREETING
                break
            case BuildSrcChange.ADD_SOURCE:
                writeGreetTask "AnotherTask", CHANGED_GREETING
                break
            case BuildSrcChange.CHANGE_RESOURCE:
                writeBuildSrcResource "42"
                break
            case BuildSrcChange.ADD_RESOURCE:
                writeBuildSrcResource ""
                break
        }
    }

    private enum BuildSrcChange {
        CHANGE_SOURCE("change source file"),
        ADD_SOURCE("add source file"),
        CHANGE_RESOURCE("change resource"),
        ADD_RESOURCE("add resource")

        final String description

        private BuildSrcChange(String description) {
            this.description = description
        }

        @Override
        String toString() {
            description
        }
    }

    private void writeGreetTask(String className, String greeting) {
        file("buildSrc/src/main/java/${className}.java").text = """
            public class $className extends ${DefaultTask.name} {
                @${TaskAction.name} void greet() { System.out.println("$greeting"); }
            }
        """
    }

    private void writeBuildSrcResource(String text) {
        file("buildSrc/src/main/resources/resource.txt").text = text
    }
}
