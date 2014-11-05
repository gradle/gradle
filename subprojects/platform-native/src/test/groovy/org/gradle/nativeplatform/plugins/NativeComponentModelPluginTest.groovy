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
import org.gradle.api.tasks.TaskDependency
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.PlatformContainer
import org.gradle.util.TestUtil
import spock.lang.Specification

class NativeComponentModelPluginTest extends Specification {
    final def project = TestUtil.createRootProject()
    def modelRegistryHelper = new ModelRegistryHelper(project)

    def setup() {
        project.pluginManager.apply(NativeComponentModelPlugin)
    }

    def "adds model extensions"() {
        expect:
        project.modelRegistry.get(ModelPath.path("toolChains"), ModelType.of(NativeToolChainRegistry)) != null
        project.modelRegistry.get(ModelPath.path("platforms"), ModelType.of(PlatformContainer)) != null
        project.modelRegistry.get(ModelPath.path("buildTypes"), ModelType.of(BuildTypeContainer)) != null
        project.modelRegistry.get(ModelPath.path("flavors"), ModelType.of(FlavorContainer)) != null
    }

    def "does not provide a default tool chain"() {
        expect:
        project.modelRegistry.get(ModelPath.path("toolChains"), ModelType.of(NativeToolChainRegistry)).isEmpty()
    }

    def "adds default flavor to every binary"() {
        when:
        project.model {
            components {
                exe(NativeExecutableSpec)
                lib(NativeLibrarySpec)
            }
        }
        project.evaluate()

        then:
        one(project.binaries.withType(NativeExecutableBinarySpec)).flavor.name == DefaultFlavor.DEFAULT
        one(project.binaries.withType(SharedLibraryBinarySpec)).flavor.name == DefaultFlavor.DEFAULT
    }

    def "behaves correctly for defaults when domain is explicitly configured"() {
        when:
        modelRegistryHelper
                .configure(NativeToolChainRegistry) { it.add toolChain("tc") }
                .configure(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
                .configure(BuildTypeContainer) { it.add named(BuildType, "bt") }
                .configure(FlavorContainer) { it.add named(Flavor, "flavor1") }

        and:
        project.evaluate()

        then:
        one(project.modelRegistry.get(ModelPath.path("toolChains"), ModelType.of(NativeToolChainRegistry))).name == 'tc'
        project.modelRegistry.get(ModelPath.path("platforms"), ModelType.of(PlatformContainer)).size() == NativePlatforms.defaultPlatformDefinitions().size() + 1 //adds one to the defaults
        one(project.modelRegistry.get(ModelPath.path("buildTypes"), ModelType.of(BuildTypeContainer))).name == 'bt'
        one(project.modelRegistry.get(ModelPath.path("flavors"), ModelType.of(FlavorContainer))).name == 'flavor1'
    }

    def "creates binaries for executable"() {
        when:
        project.pluginManager.apply(NativeComponentModelPlugin)
        modelRegistryHelper
                .configure(NativeToolChainRegistry) { it.add toolChain("tc") }
                .configure(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
                .configure(BuildTypeContainer) { it.add named(BuildType, "bt") }
                .configure(FlavorContainer) { it.add named(Flavor, "flavor1") }

        project.model {
            components {
                test(NativeExecutableSpec) {
                    targetPlatform "platform"
                }
            }
        }
        project.evaluate()

        then:
        NativeExecutableSpec executable = one(project.componentSpecs) as NativeExecutableSpec
        NativeExecutableBinarySpec executableBinary = one(project.binaries) as NativeExecutableBinarySpec
        with(executableBinary) {
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
        project.pluginManager.apply(NativeComponentModelPlugin)
        modelRegistryHelper
                .configure(NativeToolChainRegistry) { it.add toolChain("tc") }
                .configure(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
                .configure(BuildTypeContainer) { it.add named(BuildType, "bt") }
                .configure(FlavorContainer) { it.add named(Flavor, "flavor1") }

        project.model {
            components {
                test(NativeLibrarySpec) {
                    targetPlatform "platform"
                }
            }
        }
        project.evaluate()

        then:
        NativeLibrarySpec library = one(project.componentSpecs) as NativeLibrarySpec
        SharedLibraryBinarySpec sharedLibraryBinary = project.binaries.testSharedLibrary as SharedLibraryBinarySpec
        with(sharedLibraryBinary) {
            name == 'testSharedLibrary'
            component == library

            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
            flavor.name == "flavor1"
        }

        and:
        StaticLibraryBinarySpec staticLibraryBinary = project.binaries.testStaticLibrary as StaticLibraryBinarySpec
        with(staticLibraryBinary) {
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
        project.pluginManager.apply(NativeComponentModelPlugin)
        project.model {
            components {
                exe(NativeExecutableSpec)
                lib(NativeLibrarySpec)
            }
        }
        project.evaluate()

        then:
        NativeExecutableBinarySpec executableBinary = project.binaries.exeExecutable as NativeExecutableBinarySpec
        with(oneTask(executableBinary.buildDependencies)) {
            name == executableBinary.name
            group == LifecycleBasePlugin.BUILD_GROUP
        }
        SharedLibraryBinarySpec sharedLibraryBinary = project.binaries.libSharedLibrary as SharedLibraryBinarySpec
        with(oneTask(sharedLibraryBinary.buildDependencies)) {
            name == sharedLibraryBinary.name
            group == LifecycleBasePlugin.BUILD_GROUP
        }
        StaticLibraryBinarySpec staticLibraryBinary = project.binaries.libStaticLibrary as StaticLibraryBinarySpec
        with(oneTask(staticLibraryBinary.buildDependencies)) {
            name == staticLibraryBinary.name
            group == LifecycleBasePlugin.BUILD_GROUP
        }
    }

    static <T> T one(Collection<T> collection) {
        assert collection.size() == 1
        return collection.iterator().next()
    }

    public <T> T named(Class<T> type, def name) {
        Stub(type) {
            getName() >> name
        }
    }

    def toolChain(def name) {
        Stub(NativeToolChainInternal) {
            getName() >> name
            select(_) >> Stub(PlatformToolProvider) {
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
