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

class SwiftSharedLibraryLinkageIntegrationTest extends AbstractSwiftIntegrationTest {
    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Swift", ":linkDebug${variant.capitalize()}"]
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new SwiftLib()
    }

    @Override
    protected void makeSingleProject() {
        buildFile << "apply plugin: 'swift-library'"
    }

    @Override
    String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSwift"
    }

    @Override
    void assertComponentUnderTestWasBuilt() {
        file("build/modules/main/debug/${componentUnderTest.moduleName}.swiftmodule").assertIsFile()
        sharedLibrary("build/lib/main/debug/${componentUnderTest.moduleName}").assertExists()
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    def "can create shared library binary when explicitly request a shared linkage"() {
        def library = new SwiftLib()
        buildFile << """
            apply plugin: 'swift-library'

            library {
                linkage = [Linkage.SHARED]
            }
        """
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assemble')

        then:
        result.assertTasksExecuted(':compileDebugSwift', ':linkDebug', ':assemble')
        sharedLibrary('build/lib/main/debug/Foo').assertExists()
    }
}
