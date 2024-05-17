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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.nativeplatform.fixtures.app.SourceElement

class CppStaticLibraryLinkageIntegrationTest extends AbstractCppIntegrationTest {
    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-library'
            library.linkage = [Linkage.STATIC]
        """
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Cpp", ":createDebug${variant.capitalize()}"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppLib()
    }

    @ToBeFixedForConfigurationCache
    def "can create static library binary when only static linkage is specified"() {
        def library = new CppLib()
        buildFile << """
            apply plugin: 'cpp-library'

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
        result.assertTasksExecuted(':compileDebugCpp', ':createDebug', ':assemble')
        staticLibrary('build/lib/main/debug/foo').assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "can create debug and release variants of library"() {
        def library = new CppLib()
        buildFile << """
            apply plugin: 'cpp-library'

            library {
                linkage = [Linkage.STATIC]
            }
        """
        settingsFile << """
            rootProject.name = 'foo'
        """
        library.writeToProject(testDirectory)

        when:
        succeeds('assembleRelease')

        then:
        result.assertTasksExecuted(':compileReleaseCpp', ':createRelease', ':assembleRelease')
        staticLibrary('build/lib/main/release/foo').assertExists()

        when:
        succeeds('assembleDebug')

        then:
        result.assertTasksExecuted(':compileDebugCpp', ':createDebug', ':assembleDebug')
        staticLibrary('build/lib/main/debug/foo').assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "can use link file as task dependency"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'

            library {
                linkage = [Linkage.STATIC]
            }

            task assembleLinkDebug {
                dependsOn library.binaries.get { !it.optimized }.map { it.linkFile }
            }
         """

        expect:
        succeeds "assembleLinkDebug"
        result.assertTasksExecuted(':compileDebugCpp', ':createDebug', ":assembleLinkDebug")
        staticLibrary("build/lib/main/debug/hello").assertExists()
    }

    @ToBeFixedForConfigurationCache
    def "can use objects as task dependency"() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        def lib = new CppLib()
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'

            library {
                linkage = [Linkage.STATIC]
            }

            task compileDebug {
                dependsOn library.binaries.get { !it.optimized }.map { it.objects }
            }
         """

        expect:
        succeeds "compileDebug"
        result.assertTasksExecuted(":compileDebugCpp", ":compileDebug")
        objectFiles(lib.sources)*.assertExists()
        staticLibrary("build/lib/main/debug/hello").assertDoesNotExist()
    }

}
