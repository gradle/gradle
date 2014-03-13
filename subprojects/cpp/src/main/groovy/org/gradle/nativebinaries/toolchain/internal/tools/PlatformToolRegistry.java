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
import org.gradle.nativebinaries.toolchain.TargetPlatformConfiguration;
import org.gradle.nativebinaries.toolchain.internal.ToolType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlatformToolRegistry implements ToolRegistry {
    private final Map<ToolType, GccToolInternal> gccTools = new HashMap<ToolType, GccToolInternal>();

    public PlatformToolRegistry(ToolRegistry defaultTools, TargetPlatformConfiguration configuration) {
        // TODO:DAZ Replace TargetPlatformConfiguration with an action that applies to the entire registry.
        // This will involve exposing the tools by their 'name' in a container, and applying the action to that container.
        register(defaultTools.getTool(ToolType.CPP_COMPILER), configuration.getCppCompilerArgs());
        register(defaultTools.getTool(ToolType.C_COMPILER), configuration.getCCompilerArgs());
        register(defaultTools.getTool(ToolType.OBJECTIVECPP_COMPILER), configuration.getObjectiveCppCompilerArgs());
        register(defaultTools.getTool(ToolType.OBJECTIVEC_COMPILER), configuration.getObjectiveCCompilerArgs());
        register(defaultTools.getTool(ToolType.ASSEMBLER), configuration.getAssemblerArgs());
        register(defaultTools.getTool(ToolType.LINKER), configuration.getLinkerArgs());
        register(defaultTools.getTool(ToolType.STATIC_LIB_ARCHIVER), configuration.getStaticLibraryArchiverArgs());
    }

    private GccTool register(GccToolInternal baseTool, List<String> platformArgs) {
        return gccTools.put(baseTool.getToolType(), new PlatformGccTool(baseTool, platformArgs));
    }

    public GccToolInternal getTool(ToolType toolType) {
        return gccTools.get(toolType);
    }
}
