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

package org.gradle.nativebinaries.internal.configure
import org.gradle.api.Action
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.internal.DefaultBinaryNamingSchemeBuilder
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.Flavor
import org.gradle.nativebinaries.ProjectNativeBinary
import org.gradle.nativebinaries.SharedLibraryBinary
import org.gradle.nativebinaries.internal.DefaultExecutable
import org.gradle.nativebinaries.internal.DefaultNativeLibrary
import org.gradle.nativebinaries.internal.ProjectNativeComponentIdentifier
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal
import spock.lang.Specification

class DefaultNativeBinariesFactoryTest extends Specification {
    def resolver = Mock(NativeDependencyResolver)
    Action<ProjectNativeBinary> configAction = Mock(Action)

    def toolChain = Mock(ToolChainInternal)
    def platform = Mock(Platform)
    def buildType = Mock(BuildType)
    def flavor = Mock(Flavor)

    def id = new ProjectNativeComponentIdentifier("project", "name")

    def namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder().withComponentName("test")
    def factory = new DefaultNativeBinariesFactory(new DirectInstantiator(), configAction, resolver)

    def "creates binaries for executable"() {
        given:
        def executable = new DefaultExecutable(id)

        when:
        1 * configAction.execute(_)

        and:
        factory.createNativeBinaries(executable, namingSchemeBuilder, toolChain, platform, buildType, flavor)

        then:
        executable.binaries.size() == 1
        def binary = (executable.binaries as List)[0] as ProjectNativeBinary
        binary.name == "testExecutable"
        binary.toolChain == toolChain
        binary.targetPlatform == platform
        binary.buildType == buildType
        binary.flavor == flavor
    }

    def "creates binaries for library"() {
        given:
        def library = new DefaultNativeLibrary(id)

        when:
        2 * configAction.execute(_)

        and:
        factory.createNativeBinaries(library, namingSchemeBuilder, toolChain, platform, buildType, flavor)

        then:
        library.binaries.size() == 2
        def sharedLibrary = (library.binaries.withType(SharedLibraryBinary) as List)[0] as ProjectNativeBinary
        sharedLibrary.name == "testSharedLibrary"
        sharedLibrary.toolChain == toolChain
        sharedLibrary.targetPlatform == platform
        sharedLibrary.buildType == buildType
        sharedLibrary.flavor == flavor

        def staticLibrary = (library.binaries.withType(SharedLibraryBinary) as List)[0] as ProjectNativeBinary
        staticLibrary.name == "testSharedLibrary"
        staticLibrary.toolChain == toolChain
        staticLibrary.targetPlatform == platform
        staticLibrary.buildType == buildType
        staticLibrary.flavor == flavor
    }
}
