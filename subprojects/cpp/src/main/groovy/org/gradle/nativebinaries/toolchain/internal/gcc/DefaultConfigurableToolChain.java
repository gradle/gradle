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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.tools.*;

public class DefaultConfigurableToolChain implements ConfigurableToolChainInternal {
    private final GccToolInternal cCompiler;
    private final GccToolInternal staticLibArchiver;
    private final GccToolInternal cppCompiler;
    private final GccToolInternal objcCompiler;
    private final GccToolInternal objcppCompiler;
    private final GccToolInternal assembler;
    private final GccToolInternal linker;
    private final String name;
    private final String displayName;

    public DefaultConfigurableToolChain(DefaultToolRegistry defaultTools, String name, String displayName) {
        this.cCompiler = newConfiguredGccTool(defaultTools.getTool(ToolType.C_COMPILER));
        this.cppCompiler = newConfiguredGccTool(defaultTools.getTool(ToolType.CPP_COMPILER));
        this.assembler = newConfiguredGccTool(defaultTools.getTool(ToolType.ASSEMBLER));
        this.objcCompiler = newConfiguredGccTool(defaultTools.getTool(ToolType.OBJECTIVEC_COMPILER));
        this.objcppCompiler = newConfiguredGccTool(defaultTools.getTool(ToolType.OBJECTIVECPP_COMPILER));
        this.linker = newConfiguredGccTool(defaultTools.getTool(ToolType.LINKER));
        this.staticLibArchiver= newConfiguredGccTool(defaultTools.getTool(ToolType.STATIC_LIB_ARCHIVER));        this.name = name;
        this.displayName = displayName;
    }

    private GccToolInternal newConfiguredGccTool(GccToolInternal defaultTool) {
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

    public String getDisplayName() {
        return displayName;
    }

    public String getName() {
        return name;
    }
}