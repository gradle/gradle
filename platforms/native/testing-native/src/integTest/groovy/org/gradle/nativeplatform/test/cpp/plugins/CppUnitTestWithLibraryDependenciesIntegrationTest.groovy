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

import org.gradle.language.AbstractNativeUnitTestComponentDependenciesIntegrationTest

class CppUnitTestWithLibraryDependenciesIntegrationTest extends AbstractNativeUnitTestComponentDependenciesIntegrationTest {
    @Override
    protected void makeTestSuiteAndComponentWithLibrary() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
            apply plugin: 'cpp-library'
            project(':lib') {
                apply plugin: 'cpp-library'
            }
"""
        file("src/main/headers/main_lib.h") << """
            extern int some_func();
"""
        file("src/main/cpp/main_lib.cpp") << """
            #include <lib.h>
            #include <main_lib.h>
            
            int some_func() { 
                lib_func();
                return 0; 
            }
"""
        file("src/test/cpp/main.cpp") << """
            #include <main_lib.h>            
            int main() { 
                some_func();
                return 0; 
            }
"""
        file("lib/src/main/public/lib.h") << """
            #ifdef _WIN32
            #define EXPORT_FUNC __declspec(dllexport)
            #else
            #define EXPORT_FUNC
            #endif
            
            void EXPORT_FUNC lib_func();
"""
        file("lib/src/main/cpp/lib.cpp") << """
            #include <lib.h>
            void lib_func() { }
"""
    }

    @Override
    protected String getProductionComponentDsl() {
        return "library"
    }

    @Override
    protected List<String> getRunTestTasks() {
        return [":compileDebugCpp", ":compileTestCpp", ":linkTest", ":installTest", ":runTest"]
    }

    @Override
    protected List<String> getLibDebugTasks() {
        return [":lib:compileDebugCpp", ":lib:linkDebug"]
    }
}
