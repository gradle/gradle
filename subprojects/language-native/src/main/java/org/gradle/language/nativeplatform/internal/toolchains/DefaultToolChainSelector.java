/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.toolchains;

import org.gradle.internal.Cast;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppPlatform;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.language.swift.internal.DefaultSwiftPlatform;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;

public class DefaultToolChainSelector implements ToolChainSelector {
    private final ModelRegistry modelRegistry;
    private DefaultNativePlatform host;

    @Inject
    public DefaultToolChainSelector(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
        this.host = DefaultNativePlatform.host();
    }

    @Override
    public <T> Result<T> select(Class<T> platformType, T requestPlatform) {
        if (CppPlatform.class.isAssignableFrom(platformType)) {
            return Cast.uncheckedCast(select((CppPlatform) requestPlatform));
        } else if (SwiftPlatform.class.isAssignableFrom(platformType)) {
            return Cast.uncheckedCast(select((SwiftPlatform) requestPlatform));
        } else {
            throw new IllegalArgumentException("Unknown type of platform " + platformType);
        }

    }

    public Result<CppPlatform> select(CppPlatform requestPlatform) {
        DefaultNativePlatform targetNativePlatform = newNativePlatform(requestPlatform.getTargetMachine());

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider

        NativeLanguage sourceLanguage = NativeLanguage.CPP;
        NativeToolChainInternal toolChain = getToolChain(sourceLanguage, targetNativePlatform);

        // TODO - don't select again here, as the selection is already performed to select the toolchain
        PlatformToolProvider toolProvider = toolChain.select(sourceLanguage, targetNativePlatform);

        CppPlatform targetPlatform = new DefaultCppPlatform(requestPlatform.getTargetMachine(), targetNativePlatform);
        return new DefaultResult<CppPlatform>(toolChain, toolProvider, targetPlatform);
    }

    public Result<SwiftPlatform> select(SwiftPlatform requestPlatform) {
        DefaultNativePlatform targetNativePlatform = newNativePlatform(requestPlatform.getTargetMachine());

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider

        NativeLanguage sourceLanguage = NativeLanguage.SWIFT;
        NativeToolChainInternal toolChain = getToolChain(sourceLanguage, targetNativePlatform);

        // TODO - don't select again here, as the selection is already performed to select the toolchain
        PlatformToolProvider toolProvider = toolChain.select(sourceLanguage, targetNativePlatform);

        SwiftVersion sourceCompatibility = requestPlatform.getSourceCompatibility();
        if (sourceCompatibility == null && toolProvider.isAvailable()) {
            sourceCompatibility = toSwiftVersion(toolProvider.getCompilerMetadata(ToolType.SWIFT_COMPILER).getVersion());
        }
        SwiftPlatform targetPlatform = new DefaultSwiftPlatform(requestPlatform.getTargetMachine(), sourceCompatibility, targetNativePlatform);
        return new DefaultResult<SwiftPlatform>(toolChain, toolProvider, targetPlatform);
    }

    private DefaultNativePlatform newNativePlatform(TargetMachine targetMachine) {
        return host.withArchitecture(Architectures.forInput(targetMachine.getArchitecture().getName()));
    }

    private NativeToolChainInternal getToolChain(NativeLanguage sourceLanguage, NativePlatformInternal targetNativePlatform) {
        NativeToolChainRegistryInternal registry = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class);
        NativeToolChainInternal toolChain = registry.getForPlatform(sourceLanguage, targetNativePlatform);
        toolChain.assertSupported();

        return toolChain;
    }

    static SwiftVersion toSwiftVersion(VersionNumber swiftCompilerVersion) {
        for (SwiftVersion version : SwiftVersion.values()) {
            if (version.getVersion() == swiftCompilerVersion.getMajor()) {
                return version;
            }
        }
        throw new IllegalArgumentException(String.format("Swift language version is unknown for the specified Swift compiler version (%s)", swiftCompilerVersion.toString()));
    }

    static class DefaultResult<T> implements Result<T> {
        private final NativeToolChainInternal toolChain;
        private final PlatformToolProvider platformToolProvider;
        private final T targetPlatform;

        DefaultResult(NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, T targetPlatform) {
            this.toolChain = toolChain;
            this.platformToolProvider = platformToolProvider;
            this.targetPlatform = targetPlatform;
        }

        @Override
        public NativeToolChainInternal getToolChain() {
            return toolChain;
        }

        @Override
        public T getTargetPlatform() {
            return targetPlatform;
        }

        @Override
        public PlatformToolProvider getPlatformToolProvider() {
            return platformToolProvider;
        }
    }
}
