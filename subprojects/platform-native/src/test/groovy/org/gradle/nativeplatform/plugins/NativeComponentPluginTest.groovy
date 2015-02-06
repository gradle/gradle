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

import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.util.TestUtil
import spock.lang.Specification

class NativeComponentPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(NativeComponentPlugin)
    }

    def "creates link and install task for executable"() {
        when:
        project.model {
            components {
                test(NativeExecutableSpec)
            }
        }
        project.tasks.realize()
        project.bindAllModelRules()

        then:
        NativeExecutableBinarySpec testExecutable = project.binaries.testExecutable
        with(testExecutable.tasks.link) {
            it instanceof LinkExecutable
            it.toolChain == testExecutable.toolChain
            it.targetPlatform == testExecutable.targetPlatform
            it.linkerArgs == testExecutable.linker.args
        }
    }

    def "creates link task and static archive task for library"() {
        when:
        project.model {
            components {
                test(NativeLibrarySpec)
            }
        }
        project.tasks.realize()
        project.bindAllModelRules()

        then:
        SharedLibraryBinarySpec sharedLibraryBinary = project.binaries.testSharedLibrary
        with(sharedLibraryBinary.tasks.link) {
            it instanceof LinkSharedLibrary
            it.toolChain == sharedLibraryBinary.toolChain
            it.targetPlatform == sharedLibraryBinary.targetPlatform
            it.linkerArgs == sharedLibraryBinary.linker.args
        }

        and:
        StaticLibraryBinarySpec staticLibraryBinary = project.binaries.testStaticLibrary
        with(staticLibraryBinary.tasks.createStaticLib) {
            it instanceof CreateStaticLibrary
            it.toolChain == staticLibraryBinary.toolChain
            it.targetPlatform == staticLibraryBinary.targetPlatform
            it.staticLibArgs == staticLibraryBinary.staticLibArchiver.args
        }
    }
}
