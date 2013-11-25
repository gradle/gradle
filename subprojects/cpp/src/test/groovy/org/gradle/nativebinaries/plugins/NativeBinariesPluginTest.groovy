/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.plugins

import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.base.LanguageSourceSet
import org.gradle.nativebinaries.tasks.CreateStaticLibrary
import org.gradle.nativebinaries.tasks.InstallExecutable
import org.gradle.nativebinaries.tasks.LinkExecutable
import org.gradle.nativebinaries.tasks.LinkSharedLibrary
import org.gradle.util.TestUtil
import spock.lang.Specification

class NativeBinariesPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def setup() {
        project.plugins.apply(NativeBinariesPlugin)
    }

    def "creates link and install task for executable"() {
        when:
        project.executables.create "test"
        project.evaluate()

        then:
        def testExecutable = project.binaries.testExecutable
        with (project.tasks.linkTestExecutable) {
            it instanceof LinkExecutable
            it == testExecutable.tasks.link
            it.toolChain == testExecutable.toolChain
            it.targetPlatform == testExecutable.targetPlatform
            it.linkerArgs == testExecutable.linker.args
        }
        testExecutable.tasks.createStaticLib == null

        and:
        def lifecycleTask = project.tasks.testExecutable
        lifecycleTask TaskDependencyMatchers.dependsOn("linkTestExecutable")

        and:
        project.tasks.installTestExecutable instanceof InstallExecutable
    }

    def "creates link task and static archive task for library"() {
        when:
        project.libraries.create "test"
        project.evaluate()

        then:
        def sharedLibraryBinary = project.binaries.testSharedLibrary
        with (project.tasks.linkTestSharedLibrary) {
            it instanceof LinkSharedLibrary
            it == sharedLibraryBinary.tasks.link
            it.toolChain == sharedLibraryBinary.toolChain
            it.targetPlatform == sharedLibraryBinary.targetPlatform
            it.linkerArgs == sharedLibraryBinary.linker.args
        }
        sharedLibraryBinary.tasks.createStaticLib == null

        and:
        def sharedLibTask = project.tasks.testSharedLibrary
        sharedLibTask TaskDependencyMatchers.dependsOn("linkTestSharedLibrary")

        and:
        def staticLibraryBinary = project.binaries.testStaticLibrary
        with (project.tasks.createTestStaticLibrary) {
            it instanceof  CreateStaticLibrary
            it == staticLibraryBinary.tasks.createStaticLib
            it.toolChain == staticLibraryBinary.toolChain
            it.targetPlatform == staticLibraryBinary.targetPlatform
            it.staticLibArgs == staticLibraryBinary.staticLibArchiver.args
        }
        staticLibraryBinary.tasks.link == null

        and:
        def staticLibTask = project.tasks.testStaticLibrary
        staticLibTask TaskDependencyMatchers.dependsOn("createTestStaticLibrary")
    }

    def "attaches existing functional source set with same name to component"() {
        def languageSourceSet = Stub(LanguageSourceSet) {
            getName() >> "languageSourceSet"
        }

        when:
        project.sources.create "testExe"
        project.sources.testExe.add languageSourceSet

        project.executables.create "testExe"

        then:
        project.executables.testExe.source == [languageSourceSet] as Set
    }

    def "creates and attaches functional source set with same name to component"() {
        def languageSourceSet = Stub(LanguageSourceSet) {
            getName() >> "languageSourceSet"
        }

        when:
        // Ensure that any created functional source set has a language source set
        project.sources.all { functionalSourceSet ->
            functionalSourceSet.add languageSourceSet
        }

        project.executables.create "testExe"

        then:
        project.executables.testExe.source == [languageSourceSet] as Set
    }
}
