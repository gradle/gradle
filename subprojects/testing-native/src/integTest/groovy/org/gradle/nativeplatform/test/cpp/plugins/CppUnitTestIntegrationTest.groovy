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

package org.gradle.nativeplatform.test.cpp.plugins

import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.junit.Assume
import spock.lang.Unroll

class CppUnitTestIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def setup() {
        // TODO - currently the customizations to the tool chains are ignored by the plugins, so skip these tests until this is fixed
        Assume.assumeTrue(worksWithCppPlugin(toolChain))
    }

    static boolean worksWithCppPlugin(AvailableToolChains.ToolChainCandidate toolChain) {
        toolChain.id != "mingw" && toolChain.id != "gcccygwin"
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """
    }

    @Override
    protected String getMainComponentDsl() {
        return "unitTest"
    }

    @Unroll
    def "can run test executable using lifecycle task #task"() {
        def app = new CppHelloWorldApp()
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-unit-test'
        """

        app.library.writeSources(file("src/main"))
        app.simpleTestExecutable.writeSources(file("src/test"))

        when:
        succeeds(task)

        then:
        result.assertTasksExecuted(":compileDebugCpp",
            ":compileTestCpp", ":linkTest", ":installTest", ":runTest", expectedLifecycleTasks)

        where:
        task    | expectedLifecycleTasks
        "test"  | [":test"]
        "check" | [":test", ":check"]
        "build" | [":test", ":check", ":build", ":linkDebug", ":assemble"]
    }

    def "does nothing when cpp-library or cpp-application are not applied and no test source"() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """

        when:
        succeeds("check")

        then:
        result.assertTasksExecuted( ":compileTestCpp", ":linkTest", ":installTest", ":runTest", ":test", ":check")
        result.assertTasksSkipped( ":compileTestCpp", ":linkTest", ":installTest", ":runTest", ":test", ":check")
    }

    def "builds and runs test suite when no main component"() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """

        file("src/test/cpp/test.cpp") << """
int main() {
    return 0;
}
"""

        when:
        succeeds("check")

        then:
        result.assertTasksExecuted( ":compileTestCpp", ":linkTest", ":installTest", ":runTest", ":test", ":check")
    }

    def "test fails when application returns non-zero status"() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """

        file("src/test/cpp/test.cpp") << """
int main() {
    return 1;
}
"""

        when:
        fails("check")

        then:
        result.assertTasksExecuted( ":compileTestCpp", ":linkTest", ":installTest", ":runTest")
        failure.assertHasDescription("Execution failed for task ':runTest'.")
        failure.assertHasCause("There were failing tests. See the results at:")
    }
}
