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

import org.gradle.language.AbstractNativeDependenciesIntegrationTest

class CppUnitTestDependenciesIntegrationTest extends AbstractNativeDependenciesIntegrationTest {
    @Override
    protected void makeComponentWithLibrary() {
        buildFile << """
            apply plugin: 'cpp-unit-test'
            project(':lib') {
                apply plugin: 'cpp-library'
            }
"""
        file("src/test/cpp/main.cpp") << """
            #include <lib.h>
            
            int main() { 
                lib_func();
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
    protected String getComponentUnderTestDsl() {
        return "unitTest"
    }

    @Override
    protected String getAssembleDevBinaryTask() {
        return ":installTest"
    }

    @Override
    protected List<String> getAssembleDevBinaryTasks() {
        return [":compileTestCpp", ":linkTest"]
    }

    @Override
    protected List<String> getLibDebugTasks() {
        return [":lib:compileDebugCpp", ":lib:linkDebug"]
    }
}
