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


import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftSourceElement

class SwiftStaticLibraryLinkageIntegrationTest extends AbstractSwiftIntegrationTest {
    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Swift", ":createDebug${variant.capitalize()}"]
    }

    @Override
    protected SwiftSourceElement getComponentUnderTest() {
        return new SwiftLib()
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'swift-library'
            library.linkage = [Linkage.STATIC]
        """
    }

    @Override
    String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSwift"
    }

    @Override
    void assertComponentUnderTestWasBuilt() {
        file("build/modules/main/debug/${componentUnderTest.moduleName}.swiftmodule").assertIsFile()
        staticLibrary("build/lib/main/debug/${componentUnderTest.moduleName}").assertExists()
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    def "can create static only library"() {
        def library = new SwiftLib()
        buildFile << """
            apply plugin: 'swift-library'

            library {
                linkage = [Linkage.STATIC]
            }
        """
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assemble')

        then:
        result.assertTasksExecuted(':compileDebugSwift', ':createDebug', ':assemble')
        staticLibrary('build/lib/main/debug/Foo').assertExists()
    }

    def "can create debug and release variants"() {
        def library = new SwiftLib()
        buildFile << """
            apply plugin: 'swift-library'

            library {
                linkage = [Linkage.STATIC]
            }
        """
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assembleDebug')

        then:
        result.assertTasksExecuted(':compileDebugSwift', ':createDebug', ':assembleDebug')
        staticLibrary('build/lib/main/debug/Foo').assertExists()

        when:
        succeeds('assembleRelease')

        then:
        result.assertTasksExecuted(':compileReleaseSwift', ':createRelease', ':assembleRelease')
        staticLibrary('build/lib/main/release/Foo').assertExists()
    }

    def "can use link file as task dependency"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = 'foo'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'

            library {
                linkage = [Linkage.STATIC]
            }

            task assembleLinkDebug {
                dependsOn library.binaries.getByName('mainDebug').map { it.linkFile }
            }
         """

        expect:
        succeeds "assembleLinkDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":createDebug", ":assembleLinkDebug")
        staticLibrary("build/lib/main/debug/Foo").assertExists()
    }

    def "can use objects as task dependency"() {
        given:
        def lib = new SwiftLib()
        settingsFile << "rootProject.name = 'foo'"
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-library'

            library {
                linkage = [Linkage.STATIC]
            }

            task compileDebug {
                dependsOn library.binaries.getByName('mainDebug').map { it.objects }
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(":compileDebugSwift", ":compileDebug")
        objectFiles(lib)*.assertExists()
        staticLibrary("build/lib/main/debug/Foo").assertDoesNotExist()
    }
}
