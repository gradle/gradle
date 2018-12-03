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

class CppSharedLibraryLinkageIntegrationTest extends AbstractCppIntegrationTest {

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'cpp-library'
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary(String variant) {
        return [":compileDebug${variant.capitalize()}Cpp", ":linkDebug${variant.capitalize()}"]
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugCpp"
    }

    @Override
    protected SourceElement getComponentUnderTest() {
        return new CppLib()
    }

    def "can create shared library binary when only shared linkage is specified"() {
        def library = new CppLib()
        buildFile << """
            apply plugin: 'cpp-library'

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
        result.assertTasksExecuted(':compileDebugCpp', ':linkDebug', ':assemble')
        sharedLibrary('build/lib/main/debug/foo').assertExists()
    }
}
