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

package org.gradle.language.swift.internal;

import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultSwiftToolChainSelector implements SwiftToolChainSelector {
    private final ModelRegistry modelRegistry;

    @Inject
    public DefaultSwiftToolChainSelector(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public Result select() {
        DefaultNativePlatform targetPlatform = new DefaultNativePlatform("current");
        NativeToolChainInternal toolChain = (NativeToolChainInternal) modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(targetPlatform);
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);

        return new DefaultResult(toolChain, targetPlatform, toolProvider);
    }

    class DefaultResult implements Result {
        private final NativeToolChain toolChain;
        private final NativePlatform targetPlatform;
        private final PlatformToolProvider platformToolProvider;

        public DefaultResult(NativeToolChain toolChain, NativePlatform targetPlatform, PlatformToolProvider platformToolProvider) {
            this.toolChain = toolChain;
            this.targetPlatform = targetPlatform;
            this.platformToolProvider = platformToolProvider;
        }

        @Override
        public NativeToolChain getToolChain() {
            return toolChain;
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
