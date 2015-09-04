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
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.PlatformContainer
import org.gradle.util.TestUtil
import spock.lang.Specification

class NativeComponentModelPluginTest extends Specification {
    final def project = TestUtil.createRootProject()
    def modelRegistryHelper = new ModelRegistryHelper(project)

    def setup() {
        project.pluginManager.apply(NativeComponentModelPlugin)
    }

    public <T> T realizeModelElement(String path, Class<T> type) {
        realizeModelElement(path, ModelType.of(type))
    }

    public <T> T realizeModelElement(String path, ModelType<T> type) {
        project.modelRegistry.realize(ModelPath.path(path), type)
    }

    def "adds model extensions"() {
        expect:
        realizeModelElement("toolChains", NativeToolChainRegistry) != null
        realizeModelElement("platforms", PlatformContainer) != null
        realizeModelElement("buildTypes", BuildTypeContainer) != null
        realizeModelElement("flavors", FlavorContainer) != null
    }

    def "does not provide a default tool chain"() {
        expect:
        realizeModelElement("toolChains", NativeToolChainRegistry).isEmpty()
    }

    def "adds default flavor to every binary"() {
        when:
        project.model {
            components {
                exe(NativeExecutableSpec)
                lib(NativeLibrarySpec)
            }
        }
        realize()

        then:
        one(project.binaries.withType(NativeExecutableBinarySpec)).flavor.name == DefaultFlavor.DEFAULT
        one(project.binaries.withType(SharedLibraryBinarySpec)).flavor.name == DefaultFlavor.DEFAULT
    }

    def "behaves correctly for defaults when domain is explicitly configured"() {
        when:
        modelRegistryHelper
                .mutate(NativeToolChainRegistry) { it.add toolChain("tc") }
                .mutate(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
                .mutate(BuildTypeContainer) { it.add named(BuildType, "bt") }
                .mutate(FlavorContainer) { it.add named(Flavor, "flavor1") }

        and:
        realize()

        then:
        one(realizeModelElement("toolChains", NativeToolChainRegistry)).name == 'tc'
        realizeModelElement("platforms", PlatformContainer).size() == 1
        one(realizeModelElement("buildTypes", BuildTypeContainer)).name == 'bt'
        one(realizeModelElement("flavors", FlavorContainer)).name == 'flavor1'
    }

    def "creates binaries for executable"() {
        when:
        project.pluginManager.apply(NativeComponentModelPlugin)
        modelRegistryHelper
                .mutate(NativeToolChainRegistry) { it.add toolChain("tc") }
                .mutate(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
                .mutate(BuildTypeContainer) { it.add named(BuildType, "bt") }
                .mutate(FlavorContainer) { it.add named(Flavor, "flavor1") }

        project.model {
            components {
                test(NativeExecutableSpec) {
                    targetPlatform "platform"
                }
            }
        }
        realize()

        then:
        NativeExecutableSpec executable = one(realizeModelElement("components", ModelTypes.modelMap(ComponentSpec)).values()) as NativeExecutableSpec
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
        executable.binaries.values() == [executableBinary]
    }

    def "creates binaries for library"() {
        when:
        project.pluginManager.apply(NativeComponentModelPlugin)
        modelRegistryHelper
                .mutate(NativeToolChainRegistry) { it.add toolChain("tc") }
                .mutate(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
                .mutate(BuildTypeContainer) { it.add named(BuildType, "bt") }
                .mutate(FlavorContainer) { it.add named(Flavor, "flavor1") }

        project.model {
            components {
                test(NativeLibrarySpec) {
                    targetPlatform "platform"
                }
            }
        }
        realize()

        then:
        NativeLibrarySpec library = one(realizeModelElement("components", ModelTypes.modelMap(ComponentSpec)).values()) as NativeLibrarySpec
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
        library.binaries.values().contains(sharedLibraryBinary)
        library.binaries.values().contains(staticLibraryBinary)
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
        realize()

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

    private void realize() {
        project.tasks.realize();
        project.bindAllModelRules()
    }

    static <T> T one(Iterable<T> iterable) {
        def iterator = iterable.iterator()
        assert iterator.hasNext()
        def item = iterator.next()
        assert !iterator.hasNext()
        return item
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
