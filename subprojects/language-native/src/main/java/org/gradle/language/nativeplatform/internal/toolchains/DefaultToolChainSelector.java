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

import org.gradle.api.model.ObjectFactory;
import org.gradle.language.cpp.CppTargetMachine;
import org.gradle.language.cpp.internal.DefaultCppTargetMachine;
import org.gradle.language.swift.SwiftTargetMachine;
import org.gradle.language.swift.internal.DefaultSwiftTargetMachine;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultToolChainSelector implements ToolChainSelector {
    private final ModelRegistry modelRegistry;
    private final ObjectFactory objectFactory;
    private DefaultNativePlatform host;

    @Inject
    public DefaultToolChainSelector(ModelRegistry modelRegistry, ObjectFactory objectFactory) {
        this.modelRegistry = modelRegistry;
        this.objectFactory = objectFactory;
        this.host = DefaultNativePlatform.host();
    }

    @Override
    public <T extends TargetMachine> Result<T> select(Class<T> platformType, TargetMachine requestedTargetMachine) {
        DefaultNativePlatform targetPlatform = host.withArchitecture(Architectures.forInput(requestedTargetMachine.getArchitecture().getName()));

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider

        NativeLanguage sourceLanguage = platformType == SwiftTargetMachine.class ? NativeLanguage.SWIFT : NativeLanguage.CPP;
        NativeToolChainRegistryInternal registry = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class);
        NativeToolChainInternal toolChain = registry.getForPlatform(sourceLanguage, targetPlatform);
        // TODO - don't select again here, as the selection is already performed to select the toolchain
        PlatformToolProvider toolProvider = toolChain.select(sourceLanguage, targetPlatform);

        // TODO - use a better name for the platforms, rather than "host"

        final T targetMachine;
        if (CppTargetMachine.class.isAssignableFrom(platformType)) {
            targetMachine = platformType.cast(new DefaultCppTargetMachine(requestedTargetMachine));
        } else if (SwiftTargetMachine.class.isAssignableFrom(platformType)) {
            targetMachine = platformType.cast(new DefaultSwiftTargetMachine(requestedTargetMachine));
        } else {
            throw new IllegalArgumentException("Unknown type of platform " + platformType);
        }
        return new DefaultResult<T>(toolChain, toolProvider, targetPlatform, targetMachine);
    }

    class DefaultResult<T extends TargetMachine> implements Result<T> {
        private final NativeToolChainInternal toolChain;
        private final PlatformToolProvider platformToolProvider;
        private final NativePlatform targetPlatform;
        private final T targetMachine;

        DefaultResult(NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativePlatform targetPlatform, T targetMachine) {
            this.toolChain = toolChain;
            this.platformToolProvider = platformToolProvider;
            this.targetPlatform = targetPlatform;
            this.targetMachine = targetMachine;
        }

        @Override
        public NativeToolChainInternal getToolChain() {
            return toolChain;
        }

        @Override
        public T getTargetMachine() {
            return targetMachine;
        }

        @Override
        public NativePlatform getTargetPlatform() {
            return targetPlatform;
        }

        @Override
        public PlatformToolProvider getPlatformToolProvider() {
            return platformToolProvider;
        }
    }
}
