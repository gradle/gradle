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
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.internal.type.ModelType
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.FlavorContainer
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import spock.lang.Issue

import static org.gradle.model.internal.type.ModelTypes.modelMap
import static org.gradle.util.internal.CollectionUtils.single

class NativeComponentModelPluginTest extends AbstractProjectBuilderSpec {
    def registry

    def setup() {
        registry = project.modelRegistry
        project.pluginManager.apply(NativeComponentModelPlugin)
    }

    def "can apply plugin by id"() {
        given:
        def project = TestUtil.createRootProject(null)
        project.apply plugin: 'native-component-model'

        expect:
        project.plugins.hasPlugin(NativeComponentModelPlugin)
    }

    public <T> T realizeModelElement(String path, Class<T> type) {
        realizeModelElement(path, ModelType.of(type))
    }

    public <T> T realizeModelElement(String path, ModelType<T> type) {
        project.modelRegistry.realize(path, type)
    }

    ModelMap<BinarySpec> getBinaries() {
        realizeModelElement("binaries", modelMap(BinarySpec))
    }

    NativeToolChainRegistry getToolChains() {
        realizeModelElement("toolChains", NativeToolChainRegistry)
    }

    PlatformContainer getPlatforms() {
        realizeModelElement("platforms", PlatformContainer)
    }

    BuildTypeContainer getBuildTypes() {
        realizeModelElement("buildTypes", BuildTypeContainer)
    }

    FlavorContainer getFlavors() {
        realizeModelElement("flavors", FlavorContainer)
    }

    ComponentSpecContainer getComponents() {
        realizeModelElement("components", ComponentSpecContainer)
    }

    def "adds model extensions"() {
        expect:
        toolChains != null
        platforms != null
        buildTypes != null
        flavors != null
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

        then:
        single(binaries.withType(NativeExecutableBinarySpec)).flavor.name == DefaultFlavor.DEFAULT
        single(binaries.withType(SharedLibraryBinarySpec)).flavor.name == DefaultFlavor.DEFAULT
    }

    def "behaves correctly for defaults when domain is explicitly configured"() {
        when:
        registry
            .mutate(NativeToolChainRegistry) { it.add toolChain("tc") }
            .mutate(PlatformContainer) { it.add named(NativePlatformInternal, "platform") }
            .mutate(BuildTypeContainer) { it.add named(BuildType, "bt") }
            .mutate(FlavorContainer) { it.add named(Flavor, "flavor1") }

        then:
        single(toolChains).name == 'tc'
        platforms.size() == 1
        single(buildTypes).name == 'bt'
        single(flavors).name == 'flavor1'
    }

    def "creates binaries for executable"() {
        when:
        project.pluginManager.apply(NativeComponentModelPlugin)
        registry
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

        then:
        NativeExecutableSpec executable = single(components.values()) as NativeExecutableSpec
        NativeExecutableBinarySpec executableBinary = single(binaries) as NativeExecutableBinarySpec
        with(executableBinary) {
            name == 'executable'
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
        registry
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

        then:
        NativeLibrarySpec library = single(components.values()) as NativeLibrarySpec
        SharedLibraryBinarySpec sharedLibraryBinary = binaries.testSharedLibrary as SharedLibraryBinarySpec
        with(sharedLibraryBinary) {
            name == 'sharedLibrary'
            component == library

            toolChain.name == "tc"
            targetPlatform.name == "platform"
            buildType.name == "bt"
            flavor.name == "flavor1"
        }

        and:
        StaticLibraryBinarySpec staticLibraryBinary = binaries.testStaticLibrary as StaticLibraryBinarySpec
        with(staticLibraryBinary) {
            name == 'staticLibrary'
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

        then:
        NativeExecutableBinarySpec executableBinary = binaries.exeExecutable as NativeExecutableBinarySpec
        with(oneTask(executableBinary.buildDependencies)) {
            name == "exeExecutable"
            group == LifecycleBasePlugin.BUILD_GROUP
        }
        SharedLibraryBinarySpec sharedLibraryBinary = binaries.libSharedLibrary as SharedLibraryBinarySpec
        with(oneTask(sharedLibraryBinary.buildDependencies)) {
            name == "libSharedLibrary"
            group == LifecycleBasePlugin.BUILD_GROUP
        }
        StaticLibraryBinarySpec staticLibraryBinary = binaries.libStaticLibrary as StaticLibraryBinarySpec
        with(oneTask(staticLibraryBinary.buildDependencies)) {
            name == "libStaticLibrary"
            group == LifecycleBasePlugin.BUILD_GROUP
        }
    }

    public <T> T named(Class<T> type, def name) {
        Stub(type) {
            getName() >> name
        }
    }

    def toolChain(def name) {
        Stub(NativeToolChainInternal) {
            getName() >> name
            select(NativeLanguage.ANY, _) >> Stub(PlatformToolProvider) {
                isAvailable() >> true
            }
        }
    }

    Task oneTask(TaskDependency dependencies) {
        def tasks = dependencies.getDependencies(Stub(Task))
        assert tasks.size() == 1
        return tasks.asList()[0]
    }

    @Issue("GRADLE-3523")
    def "does not prevent build authors to register root nodes of type File"() {
        when:
        project.pluginManager.apply(RootFileRules)
        project.pluginManager.apply(NativeComponentModelPlugin)
        project.model {
            components {
                exe(NativeExecutableSpec)
            }
        }

        then:
        getComponents()
    }

    static class RootFileRules extends RuleSource {
        @Model
        File someFile(@Path("buildDir") File buildDir) { return new File(buildDir, "something") }
    }
}
