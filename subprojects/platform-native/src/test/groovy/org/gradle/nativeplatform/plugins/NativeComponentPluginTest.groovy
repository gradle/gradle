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

package org.gradle.nativeplatform.plugins

import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.model.ModelMap
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.platform.base.BinarySpec
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.model.internal.type.ModelTypes.modelMap

class NativeComponentPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(NativeComponentPlugin)
    }

    ModelMap<BinarySpec> realizeBinaries() {
        project.modelRegistry.find("binaries", modelMap(BinarySpec))
    }

    ModelMap<Task> realizeTasks() {
        project.modelRegistry.find 'tasks', modelMap(Task)
    }

    def "creates link and install task for executable"() {
        when:
        project.model {
            components {
                test(NativeExecutableSpec)
            }
        }

        realizeTasks()
        def binaries = realizeBinaries()
        project.bindAllModelRules()

        then:
        def testExecutable = binaries.testExecutable
        with(project.tasks.linkTestExecutable) {
            it instanceof LinkExecutable
            it == testExecutable.tasks.link
            it.toolChain.get() == testExecutable.toolChain
            it.targetPlatform.get() == testExecutable.targetPlatform
            it.linkerArgs.get() == testExecutable.linker.args
        }

        and:
        def lifecycleTask = project.tasks.testExecutable
        lifecycleTask TaskDependencyMatchers.dependsOn("linkTestExecutable")

        and:
        project.tasks.installTestExecutable instanceof InstallExecutable
    }

    def "creates link task and static archive task for library"() {
        when:
        project.model {
            components {
                test(NativeLibrarySpec)
            }
        }

        realizeTasks()
        def binaries = realizeBinaries()
        project.bindAllModelRules()

        then:
        def sharedLibraryBinary = binaries.testSharedLibrary
        with(project.tasks.linkTestSharedLibrary) {
            it instanceof LinkSharedLibrary
            it == sharedLibraryBinary.tasks.link
            it.toolChain.get() == sharedLibraryBinary.toolChain
            it.targetPlatform.get() == sharedLibraryBinary.targetPlatform
            it.linkerArgs.get() == sharedLibraryBinary.linker.args
        }

        and:
        def sharedLibTask = project.tasks.testSharedLibrary
        sharedLibTask TaskDependencyMatchers.dependsOn("linkTestSharedLibrary")

        and:
        def staticLibraryBinary = binaries.testStaticLibrary
        with(project.tasks.createTestStaticLibrary) {
            it instanceof CreateStaticLibrary
            it == staticLibraryBinary.tasks.createStaticLib
            it.toolChain.get() == staticLibraryBinary.toolChain
            it.targetPlatform.get() == staticLibraryBinary.targetPlatform
            it.staticLibArgs.get() == staticLibraryBinary.staticLibArchiver.args
        }

        and:
        def staticLibTask = project.tasks.testStaticLibrary
        staticLibTask TaskDependencyMatchers.dependsOn("createTestStaticLibrary")
    }
}
