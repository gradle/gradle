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

class SwiftStaticLibraryLinkageIntegrationTest extends AbstractSwiftIntegrationTest {
    @Override
    protected List<String> getTasksToAssembleDevelopmentBinary() {
        return [":compileDebugStaticSwift", ":createDebugStatic"]
    }

    @Override
    protected void makeSingleProject() {
        buildFile << """
            apply plugin: 'swift-library'
            library.linkage = [Linkage.STATIC]
        """
    }

    @Override
    protected String getDevelopmentBinaryCompileTask() {
        return ":compileDebugStaticSwift"
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
        result.assertTasksExecuted(':compileDebugStaticSwift', ':createDebugStatic', ':assemble')
        staticLibrary('build/lib/main/debug/static/Foo').assertExists()
    }
}
