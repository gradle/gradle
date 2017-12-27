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
import org.gradle.platform.base.internal.toolchain.ToolChainInternal;

public interface NativeToolChainInternal extends NativeToolChain, ToolChainInternal<NativePlatformInternal> {
    /**
     * Locates the tools that can target the given platform.
     */
    @Override
    PlatformToolProvider select(NativePlatformInternal targetPlatform);

    /**
     * Returns a unique, opaque, getOutputType for the output produced by this toolchain on the current operating system.
     */
    String getOutputType();

    class Identifier {
        public static String identify(NativeToolChainInternal toolChain, NativePlatformInternal platform) {
            return toolChain.getOutputType() + ":" + platform.getArchitecture().getName() + ":" + platform.getOperatingSystem().getName();
        }
    }
}
