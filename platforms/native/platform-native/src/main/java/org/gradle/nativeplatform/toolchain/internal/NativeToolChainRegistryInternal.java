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
package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;

public interface NativeToolChainRegistryInternal extends NativeToolChainRegistry {

    /**
     * Registers a default ToolChain, which may later be added to the registry via {@link #addDefaultToolChains()}.
     */
    void registerDefaultToolChain(String name, Class<? extends NativeToolChain> type);

    /**
     * Adds default tool chains to the registry.
     */
    void addDefaultToolChains();

    /**
     * Selects the tool chain that can build binaries from the given source language that can run on the target machine.
     */
    NativeToolChainInternal getForPlatform(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine);
}
