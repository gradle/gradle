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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NullMarked
public class DefaultGccPlatformToolChain implements GccPlatformToolChain, ToolRegistry {
    private final NativePlatform platform;
    private boolean canUseCommandFile = true;
    private List<String> compilerProbeArgs = new ArrayList<String>();
    private final Map<ToolType, GccCommandLineToolConfigurationInternal> tools = new HashMap<ToolType, GccCommandLineToolConfigurationInternal>();

    public DefaultGccPlatformToolChain(NativePlatform platform) {
        this.platform = platform;
    }

    public boolean isCanUseCommandFile() {
        return canUseCommandFile;
    }

    public void setCanUseCommandFile(boolean canUseCommandFile) {
        this.canUseCommandFile = canUseCommandFile;
    }

    public List<String> getCompilerProbeArgs() {
        return compilerProbeArgs;
    }

    public void compilerProbeArgs(String... args) {
        this.compilerProbeArgs.addAll(Arrays.asList(args));
    }

    @Nullable
    @Override
    public GccCommandLineToolConfigurationInternal getTool(ToolType toolType) {
        return tools.get(toolType);
    }

    public Collection<GccCommandLineToolConfigurationInternal> getTools() {
        return tools.values();
    }

    public Collection<GccCommandLineToolConfigurationInternal> getCompilers() {
        return Arrays.asList(tools.get(ToolType.C_COMPILER), tools.get(ToolType.CPP_COMPILER), tools.get(ToolType.OBJECTIVEC_COMPILER), tools.get(ToolType.OBJECTIVECPP_COMPILER));
    }

    public void add(DefaultGccCommandLineToolConfiguration tool) {
        tools.put(tool.getToolType(), tool);
    }

    @Override
    public NativePlatform getPlatform() {
        return platform;
    }

    @Override
    public GccCommandLineToolConfigurationInternal getcCompiler() {
        return tools.get(ToolType.C_COMPILER);
    }

    @Override
    public GccCommandLineToolConfigurationInternal getCppCompiler() {
        return tools.get(ToolType.CPP_COMPILER);
    }

    @Override
    public GccCommandLineToolConfigurationInternal getObjcCompiler() {
        return tools.get(ToolType.OBJECTIVEC_COMPILER);
    }

    @Override
    public GccCommandLineToolConfigurationInternal getObjcppCompiler() {
        return tools.get(ToolType.OBJECTIVECPP_COMPILER);
    }

    @Override
    public GccCommandLineToolConfigurationInternal getAssembler() {
        return tools.get(ToolType.ASSEMBLER);
    }

    @Override
    public GccCommandLineToolConfigurationInternal getLinker() {
        return tools.get(ToolType.LINKER);
    }

    @Override
    public GccCommandLineToolConfigurationInternal getStaticLibArchiver() {
        return tools.get(ToolType.STATIC_LIB_ARCHIVER);
    }

    public GccCommandLineToolConfigurationInternal getSymbolExtractor() {
        return tools.get(ToolType.SYMBOL_EXTRACTOR);
    }

    public GccCommandLineToolConfigurationInternal getStripper() {
        return tools.get(ToolType.STRIPPER);
    }
}
