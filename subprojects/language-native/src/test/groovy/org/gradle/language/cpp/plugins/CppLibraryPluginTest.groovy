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

package org.gradle.language.cpp.plugins

import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class CppLibraryPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = TestUtil.createRootProject(projectDir)

    def "adds compile and link tasks"() {
        given:
        def src = projectDir.file("src/main/cpp/lib.cpp").createFile()

        when:
        project.pluginManager.apply(CppLibraryPlugin)

        then:
        def compileCpp = project.tasks.compileCpp
        compileCpp instanceof CppCompile
        compileCpp.includes.files as List == [project.file("src/main/public"), project.file("src/main/headers")]
        compileCpp.source.files as List == [src]

        def link = project.tasks.linkMain
        link instanceof LinkSharedLibrary
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(CppLibraryPlugin)

        expect:
        def compileCpp = project.tasks.compileCpp
        compileCpp.objectFileDir == project.file("build/main/objs")

        def link = project.tasks.linkMain
        link.outputFile.parentFile == project.file("build/lib")

        project.setBuildDir("output")

        compileCpp.objectFileDir == project.file("output/main/objs")
        link.outputFile.parentFile == project.file("output/lib")
    }
}
