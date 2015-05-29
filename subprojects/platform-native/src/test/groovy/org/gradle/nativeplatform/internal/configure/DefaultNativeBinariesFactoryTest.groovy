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

package org.gradle.nativeplatform.internal.configure

import org.gradle.api.Action
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.internal.*
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultNativeBinariesFactoryTest extends Specification {
    def resolver = Mock(NativeDependencyResolver)
    Action<NativeBinarySpec> configAction = Mock(Action)

    def toolChain = Mock(NativeToolChainInternal)
    def toolProvider = Mock(PlatformToolProvider)
    def platform = Mock(NativePlatform)
    def buildType = Mock(BuildType)
    def flavor = Mock(Flavor)

    def id = new DefaultComponentSpecIdentifier("project", "name")

    def namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder().withComponentName("test")
    def instantiator = DirectInstantiator.INSTANCE;
    def binaries = Mock(ModelMap)
    def factory = new DefaultNativeBinariesFactory(binaries, configAction, resolver)
    def mainSourceSet = new DefaultFunctionalSourceSet("testFunctionalSourceSet", instantiator, Stub(ProjectSourceSet));
    def taskFactory = Mock(ITaskFactory)

    def "creates binaries for executable"() {
        given:
        def executable = BaseComponentFixtures.create(DefaultNativeExecutableSpec, new ModelRegistryHelper(), id, mainSourceSet, instantiator)
        def binary = BaseBinarySpec.create(DefaultNativeExecutableBinarySpec, "testExecutable", instantiator, taskFactory)

        when:
        factory.createNativeBinaries(executable, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)

        then:
        1 * binaries.create("testExecutable", NativeExecutableBinarySpec, { it.execute(binary); true })
        1 * configAction.execute(binary)

        and:
        binary.name == "testExecutable"
        binary.toolChain == toolChain
        binary.platformToolProvider == toolProvider
        binary.targetPlatform == platform
        binary.buildType == buildType
        binary.flavor == flavor
    }

    def "creates binaries for library"() {
        given:
        def library = BaseComponentFixtures.create(DefaultNativeLibrarySpec, new ModelRegistryHelper(), id, mainSourceSet, instantiator)
        def staticLibrary = BaseBinarySpec.create(DefaultStaticLibraryBinarySpec, "testStaticLibrary", instantiator, taskFactory)
        def sharedLibrary = BaseBinarySpec.create(DefaultSharedLibraryBinarySpec, "testSharedLibrary", instantiator, taskFactory)

        when:
        factory.createNativeBinaries(library, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)

        then:
        1 * binaries.create("testStaticLibrary", StaticLibraryBinarySpec, { it.execute(staticLibrary); true })
        1 * configAction.execute(staticLibrary)
        1 * binaries.create("testSharedLibrary", SharedLibraryBinarySpec, { it.execute(sharedLibrary); true })
        1 * configAction.execute(sharedLibrary)

        and:
        sharedLibrary.name == "testSharedLibrary"
        sharedLibrary.toolChain == toolChain
        sharedLibrary.platformToolProvider == toolProvider
        sharedLibrary.targetPlatform == platform
        sharedLibrary.buildType == buildType
        sharedLibrary.flavor == flavor

        and:
        staticLibrary.name == "testStaticLibrary"
        staticLibrary.toolChain == toolChain
        staticLibrary.platformToolProvider == toolProvider
        staticLibrary.targetPlatform == platform
        staticLibrary.buildType == buildType
        staticLibrary.flavor == flavor
    }
}
