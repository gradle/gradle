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

package org.gradle.language.swift


import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.SwiftLib

class SwiftBothLibraryLinkageIntegrationTest extends AbstractSwiftIntegrationTest {
    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebugShared${variant.capitalize()}Swift", ":linkDebugShared${variant.capitalize()}"]
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new SwiftLib()
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'swift-library'
            library.linkage = [Linkage.SHARED, Linkage.STATIC]
        """
    }

    @Override
    String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSharedSwift"
    }

    @Override
    void assertComponentUnderTestWasBuilt() {
        file("build/modules/main/debug/shared/${componentUnderTest.moduleName}.swiftmodule").assertIsFile()
        sharedLibrary("build/lib/main/debug/shared/${componentUnderTest.moduleName}").assertExists()
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    def "creates shared library binary by default when both linkage specified"() {
        def library = new SwiftLib()
        makeSingleProject()
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assemble')

        then:
        result.assertTasksExecuted(':compileDebugSharedSwift', ':linkDebugShared', ':assemble')
        sharedLibrary('build/lib/main/debug/shared/Foo').assertExists()
    }

    def "can assemble static library followed by shared library"() {
        def library = new SwiftLib()
        makeSingleProject()
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assembleDebugStatic')

        then:
        result.assertTasksExecuted(':compileDebugStaticSwift', ':createDebugStatic', ':assembleDebugStatic')
        staticLibrary('build/lib/main/debug/static/Foo').assertExists()

        when:
        succeeds('assembleDebugShared')

        then:
        result.assertTasksExecuted(':compileDebugSharedSwift', ':linkDebugShared', ':assembleDebugShared')
        sharedLibrary('build/lib/main/debug/shared/Foo').assertExists()
    }
}
