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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.DefaultFlavor
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.platform.PlatformContainer
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal
import org.gradle.nativebinaries.toolchain.ToolChainRegistry
import org.gradle.nativebinaries.toolchain.internal.PlatformToolChain
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal
import org.gradle.util.TestUtil
import spock.lang.Specification

class NativeComponentModelPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def setup() {
        project.plugins.apply(NativeComponentModelPlugin)
    }

    def "adds model extensions"() {
        expect:
        project.nativeRuntime.executables instanceof NamedDomainObjectContainer
        project.nativeRuntime.libraries instanceof NamedDomainObjectContainer
        project.modelRegistry.get("toolChains", ToolChainRegistry) != null
        project.modelRegistry.get("platforms", PlatformContainer) != null
        project.modelRegistry.get("buildTypes", BuildTypeContainer) != null
        project.modelRegistry.get("flavors", FlavorContainer) != null
    }

    def "adds default target platform, build type and flavor"() {
        expect:
        with (one(project.modelRegistry.get("platforms", PlatformContainer))) {
            name == 'current'
            architecture == ArchitectureInternal.TOOL_CHAIN_DEFAULT
        }
        one(project.modelRegistry.get("buildTypes", BuildTypeContainer)).name == 'debug'
        one(project.modelRegistry.get("flavors", FlavorContainer)).name == 'default'
    }

    def "does not provide a default tool chain"() {
        expect:
        project.modelRegistry.get("toolChains", ToolChainRegistry).isEmpty()
    }

    def "adds default flavor to every binary"() {
        when:
        project.nativeRuntime.executables.create "exe"
        project.nativeRuntime.libraries.create "lib"
        project.evaluate()

        then:
        one(project.binaries.withType(NativeExecutableBinary)).flavor.name == DefaultFlavor.DEFAULT
        one(project.binaries.withType(SharedLibraryBinary)).flavor.name == DefaultFlavor.DEFAULT
    }

    def "does not add defaults when domain is explicitly configured"() {
        when:
        project.model {
            toolChains {
                add named(ToolChainInternal, "tc")
            }
            platforms {
                add named(Platform, "platform")
            }
            buildTypes {
                add named(BuildType, "bt")
            }
            flavors {
                add named(Flavor, "flavor1")
            }
        }

        and:
        project.evaluate()

        then:
        one(project.modelRegistry.get("toolChains", ToolChainRegistry)).name == 'tc'
        one(project.modelRegistry.get("platforms", PlatformContainer)).name == 'platform'
        one(project.modelRegistry.get("buildTypes", BuildTypeContainer)).name == 'bt'
        one(project.modelRegistry.get("flavors", FlavorContainer)).name == 'flavor1'
    }

    def "creates binaries for executable"() {
        when:
        project.plugins.apply(NativeComponentModelPlugin)
        project.model {
            toolChains {
                add toolChain("tc")
            }
            platforms {
                add named(Platform, "platform")
            }
            buildTypes {
                add named(BuildType, "bt")
            }
            flavors {
                add named(Flavor, "flavor1")
            }
        }
        def executable = project.nativeRuntime.executables.create "test"
        project.evaluate()

        then:
        NativeExecutableBinary executableBinary = one(project.binaries) as NativeExecutableBinary
        with (executableBinary) {
            name == 'testExecutable'
            component == executable
            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
            flavor.name == "flavor1"
        }

        and:
        executable.binaries == [executableBinary] as Set
    }

    def "creates binaries for library"() {
        when:
        project.plugins.apply(NativeComponentModelPlugin)
        project.model {
            toolChains {
                add toolChain("tc")
            }
            platforms {
                add named(Platform, "platform")
            }
            buildTypes {
                add named(BuildType, "bt")
            }
            flavors {
                add named(Flavor, "flavor1")
            }
        }
        def library = project.nativeRuntime.libraries.create "test"
        project.evaluate()

        then:
        SharedLibraryBinary sharedLibraryBinary = project.binaries.testSharedLibrary as SharedLibraryBinary
        with (sharedLibraryBinary) {
            name == 'testSharedLibrary'
            component == library

            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
            flavor.name == "flavor1"
        }

        and:
        StaticLibraryBinary staticLibraryBinary = project.binaries.testStaticLibrary as StaticLibraryBinary
        with (staticLibraryBinary) {
            name == 'testStaticLibrary'
            component == library

            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
            flavor.name == "flavor1"
        }

        and:
        library.binaries.contains(sharedLibraryBinary)
        library.binaries.contains(staticLibraryBinary)
    }

    def "creates lifecycle task for each binary"() {
        when:
        project.plugins.apply(NativeComponentModelPlugin)
        def executable = project.nativeRuntime.executables.create "exe"
        def library = project.nativeRuntime.libraries.create "lib"
        project.evaluate()

        then:
        NativeExecutableBinary executableBinary = project.binaries.exeExecutable as NativeExecutableBinary
        with (oneTask(executableBinary.buildDependencies)) {
            name == executableBinary.name
            group == LifecycleBasePlugin.BUILD_GROUP
        }
        SharedLibraryBinary sharedLibraryBinary = project.binaries.libSharedLibrary as SharedLibraryBinary
        with (oneTask(sharedLibraryBinary.buildDependencies)) {
            name == sharedLibraryBinary.name
            group == LifecycleBasePlugin.BUILD_GROUP
        }
        StaticLibraryBinary staticLibraryBinary = project.binaries.libStaticLibrary as StaticLibraryBinary
        with (oneTask(staticLibraryBinary.buildDependencies)) {
            name == staticLibraryBinary.name
            group == LifecycleBasePlugin.BUILD_GROUP
        }
    }

    static <T> T one(Collection<T> collection) {
        assert collection.size() == 1
        return collection.iterator().next()
    }

    def named(Class type, def name) {
        Stub(type) {
            getName() >> name
        }
    }

    def toolChain(def name) {
        Stub(ToolChainInternal) {
            getName() >> name
            select(_) >> Stub(PlatformToolChain) {
                isAvailable() >> true
            }
        }
    }

    Task oneTask(TaskDependency dependencies) {
        def tasks = dependencies.getDependencies(Stub(Task))
        assert tasks.size() == 1
        return tasks.asList()[0]
    }
}
