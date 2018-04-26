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

import org.gradle.nativeplatform.test.AbstractNativeUnitTestIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

// cpp-unit-test + cpp-application don't work together on Windows yet
@Requires(TestPrecondition.NOT_WINDOWS)
class CppUnitTestWithApplicationIntegrationTest extends AbstractNativeUnitTestIntegrationTest {
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
    String[] getTasksToBuildAndRunUnitTest() {
        return [":compileTestCpp", ":linkTest", ":installTest", ":runTest"]
    }

    @Override
    protected String[] getTasksToCompileComponentUnderTest() {
        return [":compileDebugCpp", ":relocateMainForTest"]
    }

    @Override
    protected String[] getTasksToAssembleComponentUnderTest() {
        return [":linkDebug", ":installDebug"]
    }
}
