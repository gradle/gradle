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

package org.gradle.nativeplatform.test

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import spock.lang.Unroll

abstract class AbstractNativeUnitTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "does nothing when no source files are present"() {
        given:
        makeSingleProject()

        when:
        run("check")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test", ":check")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test", ":check")
    }

    @Unroll
    def "runs tests when #task lifecycle task executes"() {
        given:
        makeSingleProject()
        writeTests()

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, expectedLifecycleTasks)
        assertTestCasesRan()

        where:
        task    | expectedLifecycleTasks
        "test"  | [":test"]
        "check" | [":test", ":check"]
        "build" | [":test", ":check", ":build", tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, tasksToAssembleComponentUnderTest, ":assemble"]
    }

    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        given:
        makeSingleProject()
        writeTests()

        succeeds("test")

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")

        when:
        changeTestImplementation()
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")
        result.assertTasksSkipped(tasksToCompileComponentUnderTest)
        result.assertTasksNotSkipped(tasksToBuildAndRunUnitTest, ":test")
    }

    // Creates a single project build with no source
    protected abstract void makeSingleProject()

    // Writes test source for tests that all pass and main component, if any
    protected abstract void writeTests()

    // Updates the test implementation
    protected abstract void changeTestImplementation()

    // Asserts expected tests ran
    protected abstract void assertTestCasesRan()

    protected abstract String[] getTasksToBuildAndRunUnitTest()

    protected String[] getTasksToCompileComponentUnderTest() {
        return []
    }

    protected String[] getTasksToAssembleComponentUnderTest() {
        return []
    }
}
