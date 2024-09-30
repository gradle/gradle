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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.nativeplatform.fixtures.app.SourceElement

class CppBothLibraryLinkageIntegrationTest extends AbstractCppIntegrationTest {
    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebugShared${variant.capitalize()}Cpp", ":linkDebugShared${variant.capitalize()}"]
    }

    @Override
    protected String getTaskNameToAssembleDevelopmentBinaryWithArchitecture(String architecture) {
        return ":assembleDebugShared${architecture.toLowerCase().capitalize()}"
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-library'
            library.linkage = [Linkage.SHARED, Linkage.STATIC]
        """
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSharedCpp"
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppLib()
    }

    def "creates shared library binary by default when both linkage specified"() {
        def library = new CppLib()
        makeSingleProject()
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assemble')

        then:
        result.assertTasksExecuted(':compileDebugSharedCpp', ':linkDebugShared', ':assemble')
        sharedLibrary('build/lib/main/debug/shared/foo').assertExists()
    }

    def "can assemble static library followed by shared library"() {
        def library = new CppLib()
        makeSingleProject()
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assembleDebugStatic')

        then:
        result.assertTasksExecuted(':compileDebugStaticCpp', ':createDebugStatic', ':assembleDebugStatic')
        staticLibrary('build/lib/main/debug/static/foo').assertExists()

        when:
        succeeds('assembleDebugShared')

        then:
        result.assertTasksExecuted(':compileDebugSharedCpp', ':linkDebugShared', ':assembleDebugShared')
        sharedLibrary('build/lib/main/debug/shared/foo').assertExists()
    }
}
