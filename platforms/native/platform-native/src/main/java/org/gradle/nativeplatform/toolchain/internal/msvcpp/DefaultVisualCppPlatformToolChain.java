/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.CommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration;

import java.util.HashMap;
import java.util.Map;

public class DefaultVisualCppPlatformToolChain implements VisualCppPlatformToolChain {
    private final NativePlatform platform;
    protected final Map<ToolType, CommandLineToolConfigurationInternal> tools;

    public DefaultVisualCppPlatformToolChain(NativePlatform platform, Instantiator instantiator) {
        this.platform = platform;
        tools = new HashMap<ToolType, CommandLineToolConfigurationInternal>();
        tools.put(ToolType.C_COMPILER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.C_COMPILER));
        tools.put(ToolType.CPP_COMPILER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.CPP_COMPILER));
        tools.put(ToolType.LINKER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.LINKER));
        tools.put(ToolType.STATIC_LIB_ARCHIVER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER));
        tools.put(ToolType.ASSEMBLER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.ASSEMBLER));
        tools.put(ToolType.WINDOW_RESOURCES_COMPILER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.WINDOW_RESOURCES_COMPILER));
    }

    @Override
    public CommandLineToolConfiguration getcCompiler() {
        return tools.get(ToolType.C_COMPILER);
    }

    @Override
    public CommandLineToolConfiguration getCppCompiler() {
        return tools.get(ToolType.CPP_COMPILER);
    }

    @Override
    public CommandLineToolConfiguration getRcCompiler() {
        return tools.get(ToolType.WINDOW_RESOURCES_COMPILER);
    }

    @Override
    public CommandLineToolConfiguration getAssembler() {
        return tools.get(ToolType.ASSEMBLER);
    }

    @Override
    public CommandLineToolConfiguration getLinker() {
        return tools.get(ToolType.LINKER);
    }

    @Override
    public CommandLineToolConfiguration getStaticLibArchiver() {
        return tools.get(ToolType.STATIC_LIB_ARCHIVER);
    }

    @Override
    public NativePlatform getPlatform() {
        return platform;
    }
}
