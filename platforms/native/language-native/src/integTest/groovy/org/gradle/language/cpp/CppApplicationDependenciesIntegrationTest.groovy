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

package org.gradle.language.cpp

import org.gradle.language.AbstractNativeProductionComponentDependenciesIntegrationTest

class CppApplicationDependenciesIntegrationTest extends AbstractNativeProductionComponentDependenciesIntegrationTest implements CppTaskNames {
    @Override
    protected void makeComponentWithLibrary() {
        buildFile << """
            apply plugin: 'cpp-application'
            project(':lib') {
                apply plugin: 'cpp-library'
            }
        """

        file("lib/src/main/cpp/lib.cpp") << librarySource
        file("src/main/cpp/app.cpp") << applicationSource
    }

    @Override
    protected void makeComponentWithIncludedBuildLibrary() {
        buildFile << """
            apply plugin: 'cpp-application'
        """

        file('lib/build.gradle') << """
            apply plugin: 'cpp-library'
            
            group = 'org.gradle.test'
            version = '1.0'
        """
        file('lib/settings.gradle').createFile()

        file("lib/src/main/cpp/lib.cpp") << librarySource
        file("src/main/cpp/app.cpp") << applicationSource
    }

    private static String getLibrarySource() {
        return """
            #ifdef _WIN32
            #define EXPORT_FUNC __declspec(dllexport)
            #else
            #define EXPORT_FUNC
            #endif
            
            void EXPORT_FUNC lib_func() { }
        """
    }

    private static String getApplicationSource() {
        return """
            int main() {
                return 0;
            }
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "application"
    }

    @Override
    protected List<String> getAssembleDebugTasks() {
        return tasks.debug.allToAssembleWithInstall - tasks.debug.assemble
    }

    @Override
    protected List<String> getAssembleReleaseTasks() {
        return tasks.release.allToAssembleWithInstall - tasks.release.assemble
    }

    @Override
    protected List<String> getLibDebugTasks() {
        return [':lib:compileDebugCpp', ':lib:linkDebug']
    }
}
