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
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.internal.DefaultNativeExecutableSpec
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder
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
    def instantiator = new DirectInstantiator();
    def factory = new DefaultNativeBinariesFactory(instantiator, configAction, resolver)
    def mainSourceSet = new DefaultFunctionalSourceSet("testFunctionalSourceSet", instantiator, Stub(ProjectSourceSet));

    def "creates binaries for executable"() {
        given:
        def executable = BaseComponentSpec.create(DefaultNativeExecutableSpec, id, mainSourceSet, instantiator)

        when:
        1 * configAction.execute(_)

        and:
        factory.createNativeBinaries(executable, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)

        then:
        executable.binaries.size() == 1
        def binary = (executable.binaries as List)[0] as NativeBinarySpecInternal
        binary.name == "testExecutable"
        binary.toolChain == toolChain
        binary.platformToolProvider == toolProvider
        binary.targetPlatform == platform
        binary.buildType == buildType
        binary.flavor == flavor
    }

    def "creates binaries for library"() {
        given:
        def library = BaseComponentSpec.create(DefaultNativeLibrarySpec.class, id, mainSourceSet, new DirectInstantiator())

        when:
        2 * configAction.execute(_)

        and:
        factory.createNativeBinaries(library, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)

        then:
        library.binaries.size() == 2
        def sharedLibrary = (library.binaries.withType(SharedLibraryBinarySpec) as List)[0] as NativeBinarySpecInternal
        sharedLibrary.name == "testSharedLibrary"
        sharedLibrary.toolChain == toolChain
        sharedLibrary.platformToolProvider == toolProvider
        sharedLibrary.targetPlatform == platform
        sharedLibrary.buildType == buildType
        sharedLibrary.flavor == flavor

        def staticLibrary = (library.binaries.withType(SharedLibraryBinarySpec) as List)[0] as NativeBinarySpecInternal
        staticLibrary.name == "testSharedLibrary"
        staticLibrary.toolChain == toolChain
        staticLibrary.platformToolProvider == toolProvider
        staticLibrary.targetPlatform == platform
        staticLibrary.buildType == buildType
        staticLibrary.flavor == flavor
    }
}
