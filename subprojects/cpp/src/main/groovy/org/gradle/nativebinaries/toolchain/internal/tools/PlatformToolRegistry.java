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
package org.gradle.nativebinaries.toolchain.internal.tools;

import org.gradle.nativebinaries.toolchain.GccTool;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.gcc.ApplyablePlatformConfiguration;

import java.util.HashMap;
import java.util.Map;

public class PlatformToolRegistry implements ToolRegistry {
    private final Map<ToolType, GccToolInternal> gccTools = new HashMap<ToolType, GccToolInternal>();

    public PlatformToolRegistry(ToolRegistry defaultTools, ApplyablePlatformConfiguration applyablePlatformConfiguration) {
        // TODO:DAZ Replace TargetPlatformConfiguration with an action that applies to the entire registry.
        DefaultConfigurableToolChain configurableToolChain = new DefaultConfigurableToolChain(
                defaultTools.getTool(ToolType.C_COMPILER),
                defaultTools.getTool(ToolType.CPP_COMPILER),
                defaultTools.getTool(ToolType.ASSEMBLER),
                defaultTools.getTool(ToolType.OBJECTIVEC_COMPILER),
                defaultTools.getTool(ToolType.OBJECTIVECPP_COMPILER),
                defaultTools.getTool(ToolType.LINKER),
                defaultTools.getTool(ToolType.STATIC_LIB_ARCHIVER));

        applyablePlatformConfiguration.apply(configurableToolChain);

        register(configurableToolChain.getCCompiler());
        register(configurableToolChain.getCppCompiler());
        register(configurableToolChain.getAssembler());
        register(configurableToolChain.getObjcCompiler());
        register(configurableToolChain.getObjcppCompiler());
        register(configurableToolChain.getLinker());
        register(configurableToolChain.getStaticLibArchiver());
    }

    private GccTool register(GccToolInternal tool) {
        return gccTools.put(tool.getToolType(), tool);
    }

    public GccToolInternal getTool(ToolType toolType) {
        return gccTools.get(toolType);
    }

    private class DefaultConfigurableToolChain implements ConfigurableToolChainInternal {
        private final GccToolInternal cCompiler;
        private final GccToolInternal staticLibArchiver;
        private final GccToolInternal cppCompiler;
        private final GccToolInternal objcCompiler;
        private final GccToolInternal objcppCompiler;
        private final GccToolInternal assembler;
        private final GccToolInternal linker;

        public DefaultConfigurableToolChain(GccToolInternal cCompilerTool,
                                            GccToolInternal cppCompilerTool,
                                            GccToolInternal assemblerTool,
                                            GccToolInternal objcCompiler,
                                            GccToolInternal objcppCompiler,
                                            GccToolInternal linkerTool,
                                            GccToolInternal staticLibArchiverTool) {

            this.cCompiler = newPlatformGccTool(cCompilerTool);
            this.cppCompiler = newPlatformGccTool(cppCompilerTool);
            this.assembler = newPlatformGccTool(assemblerTool);
            this.objcCompiler = newPlatformGccTool(objcCompiler);
            this.objcppCompiler = newPlatformGccTool(objcppCompiler);
            this.linker = newPlatformGccTool(linkerTool);
            this.staticLibArchiver= newPlatformGccTool(staticLibArchiverTool);
        }

        private GccToolInternal newPlatformGccTool(GccToolInternal defaultTool) {
            DefaultTool platformTool = new DefaultTool(defaultTool.getToolType(), defaultTool.getExecutable());
            platformTool.withArguments(defaultTool.getArgAction());
            return platformTool;
        }

        public GccToolInternal getCCompiler() {
            return cCompiler;
        }

        public GccToolInternal getCppCompiler() {
            return cppCompiler;
        }

        public GccToolInternal getAssembler() {
            return assembler;
        }

        public GccToolInternal getLinker() {
            return linker;
        }

        public GccToolInternal getStaticLibArchiver() {
            return staticLibArchiver;
        }

        public GccToolInternal getObjcCompiler() {
            return objcCompiler;
        }

        public GccToolInternal getObjcppCompiler() {
            return objcppCompiler;
        }
    }
}
