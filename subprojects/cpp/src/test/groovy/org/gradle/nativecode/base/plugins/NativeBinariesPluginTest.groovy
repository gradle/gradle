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

package org.gradle.nativecode.base.plugins

import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.TaskDependency
import org.gradle.nativecode.base.ExecutableBinary
import org.gradle.nativecode.language.cpp.plugins.CppPlugin
import org.gradle.util.HelperUtil
import spock.lang.Specification

class NativeBinariesPluginTest extends Specification {
    final def project = HelperUtil.createRootProject()

    def "creates domain objects and lifecycle task for executable"() {
        given:
        dsl {
            apply plugin: CppPlugin
            executables {
                test {
                }
            }
        }

        expect:
        def executable = project.executables.test

        and:
        ExecutableBinary executableBinary = project.binaries.testExecutable
        executableBinary.component == executable
        executableBinary.toolChain
        executableBinary.outputFile == project.file("build/binaries/testExecutable/${executableBinary.toolChain.getExecutableName('test')}")

        and:
        executable.binaries.contains executableBinary

        and:
        with (oneTask(executableBinary.buildDependencies)) {
            name == executableBinary.name
            group == BasePlugin.BUILD_GROUP
        }
    }

    def "creates domain objects and lifecycle tasks for library"() {
        when:
        dsl {
            apply plugin: NativeBinariesPlugin
            libraries {
                test {
                }
            }
        }

        then:
        def sharedLibName = project.toolChains.defaultToolChain.getSharedLibraryName("test")
        def staticLibName = project.toolChains.defaultToolChain.getStaticLibraryName("test")
        def library = project.libraries.test

        and:
        def sharedLibraryBinary = project.binaries.testSharedLibrary
        sharedLibraryBinary.toolChain
        sharedLibraryBinary.outputFile == project.file("build/binaries/testSharedLibrary/$sharedLibName")
        sharedLibraryBinary.component == project.libraries.test

        and:
        def staticLibraryBinary = project.binaries.testStaticLibrary
        staticLibraryBinary.toolChain
        staticLibraryBinary.outputFile == project.file("build/binaries/testStaticLibrary/$staticLibName")
        staticLibraryBinary.component == project.libraries.test

        and:
        library.binaries.contains(sharedLibraryBinary)
        library.binaries.contains(staticLibraryBinary)

        and:
        with (oneTask(sharedLibraryBinary.buildDependencies)) {
            name == sharedLibraryBinary.name
            group == BasePlugin.BUILD_GROUP
        }
        with (oneTask(staticLibraryBinary.buildDependencies)) {
            name == staticLibraryBinary.name
            group == BasePlugin.BUILD_GROUP
        }
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }

    Task oneTask(TaskDependency dependencies) {
        def tasks = dependencies.getDependencies(Stub(Task))
        assert tasks.size() == 1
        return tasks.asList()[0]
    }
}
