/*
 * Copyright 2018 the original author or authors.
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

class CppUnitTestWithApplicationIntegrationTest extends AbstractCppUnitTestIntegrationTest {
    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
            apply plugin: 'cpp-application'
        """
    }

    @Override
    protected void writeTests() {
        file("src/main/cpp/app.cpp") << """
            #include <app.h>
            int app() {
                return 0;
            }
            int main() {
                return app();
            }
        """
        file("src/main/headers/app.h") << """
            extern int app();
        """
        file("src/test/headers/tests.h") << """
        """
        file("src/test/cpp/test_app.cpp") << """
            #include <app.h>
            #include <tests.h>
            int main() {
                return app();
            }
        """
    }

    @Override
    protected void changeTestImplementation() {
        file("src/test/cpp/test_app.cpp") << """
            void test_func() { }
        """
    }

    @Override
    protected void assertTestCasesRan() {
        // Ok
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "application"
    }

    @Override
    protected String[] getTasksToBuildAndRunUnitTest(String architecture) {
        return super.getTasksToBuildAndRunUnitTest(architecture) + tasks.withArchitecture(architecture).test.relocate
    }

    @Override
    protected String[] getTasksToCompileComponentUnderTest(String architecture) {
        def debugTasks = tasks.withArchitecture(architecture).debug
        return [debugTasks.compile]
    }

    @Override
    protected String[] getTasksToAssembleComponentUnderTest(String architecture) {
        def debugTasks = tasks.withArchitecture(architecture).debug
        return [debugTasks.link, debugTasks.install]
    }

    @Override
    protected String[] getTasksToRelocate(String architecture) {
        return tasks.withArchitecture(architecture).test.relocate
    }
}
