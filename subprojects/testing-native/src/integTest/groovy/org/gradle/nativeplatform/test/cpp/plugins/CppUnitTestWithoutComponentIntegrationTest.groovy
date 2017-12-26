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

import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.test.AbstractNativeUnitTestIntegrationTest
import org.junit.Assume

class CppUnitTestWithoutComponentIntegrationTest extends AbstractNativeUnitTestIntegrationTest {
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
        result.assertTasksExecuted( tasksToBuildAndRunUnitTest, ":test", ":check")
    }

    def "test fails when test executable returns non-zero status"() {
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
        result.assertTasksExecuted( tasksToBuildAndRunUnitTest)
        failure.assertHasDescription("Execution failed for task ':runTest'.")
        failure.assertHasCause("There were failing tests. See the results at:")
    }

    @Override
    String[] getTasksToBuildAndRunUnitTest() {
        return [":compileTestCpp", ":linkTest", ":installTest", ":runTest"]
    }
}
