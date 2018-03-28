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
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultToolChainSelectorTest extends Specification {
    def modelRegistry = Stub(ModelRegistry)
    def os = Stub(OperatingSystemInternal)
    def arch = Stub(ArchitectureInternal)
    def host = new DefaultNativePlatform("host", os, arch)
    def objectFactory = Mock(ObjectFactory)
    def selector = new DefaultToolChainSelector(modelRegistry, objectFactory)

    def setup() {
        selector.host = host
    }

    def "selects C++ toolchain for host machine"() {
        def registry = Mock(NativeToolChainRegistryInternal)
        def toolChain = Mock(NativeToolChainInternal)
        def toolProvider = Mock(PlatformToolProvider)


        given:
        objectFactory.named(_, _) >> Mock(OperatingSystemFamily)
        modelRegistry.realize(_, NativeToolChainRegistryInternal) >> registry

        when:
        def result = selector.select(CppPlatform)

        then:
        result.toolChain == toolChain
        result.targetPlatform instanceof CppPlatform
        result.targetPlatform.operatingSystem == os
        result.targetPlatform.architecture == arch
        result.platformToolProvider == toolProvider

        and:
        registry.getForPlatform(NativeLanguage.CPP, host) >> toolChain
        toolChain.select(NativeLanguage.CPP, host) >> toolProvider
    }

    def "selects toolchain for x86 architecture when host is Windows x64 and x64 tools are not available"() {
        def registry = Mock(NativeToolChainRegistryInternal)
        def toolChain = Mock(NativeToolChainInternal)
        def toolProvider = Mock(PlatformToolProvider)
        def x86ToolChain = Mock(NativeToolChainInternal)
        def x86ToolProvider = Mock(PlatformToolProvider)
        def windows = Mock(OperatingSystemFamily)

        given:
        objectFactory.named(_, _) >> windows
        modelRegistry.realize(_, NativeToolChainRegistryInternal) >> registry
        windows.isWindows() >> true
        os.windows >> true
        arch.amd64 >> true

        when:
        def result = selector.select(CppPlatform)

        then:
        result.toolChain == x86ToolChain
        result.targetPlatform instanceof CppPlatform
        result.targetPlatform.operatingSystem == os
        result.targetPlatform.architecture.name == "x86"
        result.platformToolProvider == x86ToolProvider

        and:
        1 * registry.getForPlatform(NativeLanguage.CPP, host) >> toolChain
        1 * toolChain.select(NativeLanguage.CPP, host) >> toolProvider
        toolProvider.available >> false
        1 * registry.getForPlatform(NativeLanguage.CPP, { it.architecture.name == "x86" }) >> x86ToolChain
        1 * x86ToolChain.select(NativeLanguage.CPP, { it.architecture.name == "x86" }) >> x86ToolProvider
        x86ToolProvider.available >> true
    }

    def "selects (broken) x64 toolchain when host is Windows x64 and no tools are not available"() {
        def registry = Mock(NativeToolChainRegistryInternal)
        def toolChain = Mock(NativeToolChainInternal)
        def toolProvider = Mock(PlatformToolProvider)
        def x86ToolChain = Mock(NativeToolChainInternal)
        def x86ToolProvider = Mock(PlatformToolProvider)
        def windows = Mock(OperatingSystemFamily)

        given:
        objectFactory.named(_, _) >> windows
        modelRegistry.realize(_, NativeToolChainRegistryInternal) >> registry
        windows.isWindows() >> true
        os.windows >> true
        arch.amd64 >> true

        when:
        def result = selector.select(CppPlatform)

        then:
        result.toolChain == toolChain
        result.targetPlatform instanceof CppPlatform
        result.targetPlatform.operatingSystem == os
        result.targetPlatform.architecture == arch
        result.platformToolProvider == toolProvider

        and:
        1 * registry.getForPlatform(NativeLanguage.CPP, host) >> toolChain
        1 * toolChain.select(NativeLanguage.CPP, host) >> toolProvider
        toolProvider.available >> false
        1 * registry.getForPlatform(NativeLanguage.CPP, { it.architecture.name == "x86" }) >> x86ToolChain
        1 * x86ToolChain.select(NativeLanguage.CPP, { it.architecture.name == "x86" }) >> x86ToolProvider
        x86ToolProvider.available >> false
    }
}
