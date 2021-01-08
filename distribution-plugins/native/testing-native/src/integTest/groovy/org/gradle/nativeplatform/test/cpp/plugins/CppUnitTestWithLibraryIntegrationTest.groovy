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

class CppUnitTestWithLibraryIntegrationTest extends AbstractCppUnitTestIntegrationTest {
    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
            apply plugin: 'cpp-library'
        """
    }

    @Override
    protected void writeTests() {
        file("src/main/cpp/lib.cpp") << """
            #include <lib.h>
            int lib() {
                return 0;
            }
        """
        file("src/main/headers/lib.h") << """
            extern int lib();
        """
        file("src/test/headers/tests.h") << """
        """
        file("src/test/cpp/test_lib.cpp") << """
            #include <lib.h>
            #include <tests.h>
            int main() {
                return lib();
            }
        """
    }

    @Override
    protected void changeTestImplementation() {
        file("src/test/cpp/test_lib.cpp") << """
            void test_func() { }
        """
    }

    @Override
    protected void assertTestCasesRan() {
        // ok
    }

    @Override
    protected String[] getTasksToCompileComponentUnderTest(String architecture) {
        def debugTasks = tasks.withArchitecture(architecture).debug
        return [debugTasks.compile]
    }

    @Override
    protected String[] getTasksToAssembleComponentUnderTest(String architecture) {
        def debugTasks = tasks.withArchitecture(architecture).debug
        return [debugTasks.link]
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }
}
