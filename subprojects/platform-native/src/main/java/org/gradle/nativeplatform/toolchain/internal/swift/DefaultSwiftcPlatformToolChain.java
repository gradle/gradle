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

package org.gradle.nativeplatform.toolchain.internal.swift;

import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.CommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.SwiftcPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration;

import java.util.HashMap;
import java.util.Map;

public class DefaultSwiftcPlatformToolChain implements SwiftcPlatformToolChain {
    private final NativePlatform platform;
    private final Map<ToolType, CommandLineToolConfigurationInternal> tools = new HashMap<ToolType, CommandLineToolConfigurationInternal>();

    public DefaultSwiftcPlatformToolChain(NativePlatform platform) {
        this.platform = platform;
    }

    public void add(DefaultCommandLineToolConfiguration tool) {
        tools.put(tool.getToolType(), tool);
    }

    @Override
    public CommandLineToolConfiguration getSwiftCompiler() {
        return tools.get(ToolType.SWIFT_COMPILER);
    }

    @Override
    public CommandLineToolConfiguration getLinker() {
        return tools.get(ToolType.LINKER);
    }

    @Override
    public NativePlatform getPlatform() {
        return platform;
    }
}
