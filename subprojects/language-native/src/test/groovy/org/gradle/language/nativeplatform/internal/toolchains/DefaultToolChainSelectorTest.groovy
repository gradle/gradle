/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.toolchains

import org.gradle.api.model.ObjectFactory
import org.gradle.language.cpp.CppPlatform
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.util.UsesNativeServices
import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.language.swift.SwiftVersion.SWIFT3
import static org.gradle.language.swift.SwiftVersion.SWIFT4

@UsesNativeServices
class DefaultToolChainSelectorTest extends Specification {
    def modelRegistry = Stub(ModelRegistry)
    def osFamily = Stub(OperatingSystemFamily)
    def machineArchitecture = Stub(MachineArchitecture)
    def targetMachine = Stub(TargetMachine) {
        getArchitecture() >> machineArchitecture
        getOperatingSystemFamily() >> osFamily
    }
    def os = Stub(OperatingSystemInternal)
    def arch = Stub(ArchitectureInternal)
    def host = new DefaultNativePlatform("host", os, arch)
    def objectFactory = Mock(ObjectFactory)
    def selector = new DefaultToolChainSelector(modelRegistry, objectFactory)

    def setup() {
        selector.host = host
    }

    def "selects C++ toolchain for the specified architecture"() {
        def registry = Mock(NativeToolChainRegistryInternal)
        def toolChain = Mock(NativeToolChainInternal)
        def toolProvider = Mock(PlatformToolProvider)

        given:
        objectFactory.named(_, _) >> Mock(OperatingSystemFamily)
        modelRegistry.realize(_, NativeToolChainRegistryInternal) >> registry
        machineArchitecture.name >> architecture

        when:
        def result = selector.select(CppPlatform, targetMachine)

        then:
        result.toolChain == toolChain
        result.targetMachine instanceof CppPlatform
        result.targetMachine.operatingSystemFamily == osFamily
        result.targetMachine.architecture == machineArchitecture
        result.targetPlatform.operatingSystem == os
        result.targetPlatform.architecture == Architectures.forInput(architecture)
        result.platformToolProvider == toolProvider

        and:
        registry.getForPlatform(NativeLanguage.CPP, _) >> { args ->
            assert args[1].architecture == Architectures.forInput(architecture)
            return toolChain
        }
        toolChain.select(NativeLanguage.CPP, _) >> { args ->
            assert args[1].architecture == Architectures.forInput(architecture)
            toolProvider
        }

        where:
        architecture << [
                MachineArchitecture.X86,
                MachineArchitecture.X86_64
        ]
    }

    @Unroll
    def "can associate the compiler version #compilerVersion to #languageVersion language version"() {
        expect:
        DefaultToolChainSelector.toSwiftVersion(VersionNumber.parse(compilerVersion)) == languageVersion

        where:
        // See https://swift.org/download
        compilerVersion | languageVersion
        '4.0.3'         | SWIFT4
        '4.0.2'         | SWIFT4
        '4.0'           | SWIFT4
        '3.1.1'         | SWIFT3
        '3.1'           | SWIFT3
        '3.0.2'         | SWIFT3
        '3.0.1'         | SWIFT3
        '3.0'           | SWIFT3
    }

    def "throws exception when Swift language is unknown for specified compiler version"() {
        when:
        DefaultToolChainSelector.toSwiftVersion(VersionNumber.parse("99.0.1"))

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Swift language version is unknown for the specified Swift compiler version (99.0.1)'
    }
}
