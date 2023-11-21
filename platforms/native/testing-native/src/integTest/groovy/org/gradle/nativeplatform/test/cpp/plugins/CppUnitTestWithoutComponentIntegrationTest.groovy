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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class CppUnitTestWithoutComponentIntegrationTest extends AbstractCppUnitTestIntegrationTest {
    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """
    }

    @Override
    protected void writeTests() {
        file("src/test/headers/tests.h") << """
            extern int test();
        """
        file("src/test/cpp/test.cpp") << """
            #include <tests.h>
            int test() {
                return 0;
            }
        """
        file("src/test/cpp/test_main.cpp") << """
            #include <tests.h>
            int main() {
                return test();
            }
        """
    }

    @Override
    protected void changeTestImplementation() {
        file("src/test/cpp/test.cpp") << """
            void test_func() { }
        """
    }

    @Override
    protected void assertTestCasesRan() {
        // Ok
    }

    @ToBeFixedForConfigurationCache
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
        result.assertTasksExecuted(tasksToBuildAndRunUnitTest)
        failure.assertHasDescription("Execution failed for task ':runTest'.")
        failure.assertHasCause("There were failing tests. See the results at:")
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return null
    }
}
