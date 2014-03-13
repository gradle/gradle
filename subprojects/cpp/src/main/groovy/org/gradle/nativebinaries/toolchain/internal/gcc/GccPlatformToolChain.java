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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.language.objectivec.internal.ObjectiveCCompileSpec;
import org.gradle.nativebinaries.language.objectivecpp.internal.ObjectiveCppCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.OutputCleaningCompiler;
import org.gradle.nativebinaries.toolchain.internal.PlatformToolChain;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.tools.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.tools.ToolSearchPath;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

class GccPlatformToolChain implements PlatformToolChain {
    private final ToolSearchPath toolSearchPath;
    private final ToolRegistry toolRegistry;
    private final ExecActionFactory execActionFactory;
    private final boolean useCommandFile;

    GccPlatformToolChain(ToolSearchPath toolSearchPath, ToolRegistry toolRegistry, ExecActionFactory execActionFactory, boolean useCommandFile) {
        this.toolRegistry = toolRegistry;
        this.toolSearchPath = toolSearchPath;
        this.execActionFactory = execActionFactory;
        this.useCommandFile = useCommandFile;
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
        CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(ToolType.CPP_COMPILER);
        CppCompiler cppCompiler = new CppCompiler(commandLineTool, toolRegistry.getTool(ToolType.CPP_COMPILER).getArgAction(), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, getOutputFileSuffix());
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(ToolType.C_COMPILER);
        CCompiler cCompiler = new CCompiler(commandLineTool, toolRegistry.getTool(ToolType.C_COMPILER).getArgAction(), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<CCompileSpec>(cCompiler, getOutputFileSuffix());
    }

    public <T extends BinaryToolSpec> Compiler<T> createObjectiveCppCompiler() {
        CommandLineTool<ObjectiveCppCompileSpec> commandLineTool = commandLineTool(ToolType.OBJECTIVECPP_COMPILER);
        ObjectiveCppCompiler objectiveCppCompiler = new ObjectiveCppCompiler(commandLineTool, toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER).getArgAction(), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<ObjectiveCppCompileSpec>(objectiveCppCompiler, getOutputFileSuffix());
    }

    public <T extends BinaryToolSpec> Compiler<T> createObjectiveCCompiler() {
        CommandLineTool<ObjectiveCCompileSpec> commandLineTool = commandLineTool(ToolType.OBJECTIVEC_COMPILER);
        ObjectiveCCompiler objectiveCCompiler = new ObjectiveCCompiler(commandLineTool, toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER).getArgAction(), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<ObjectiveCCompileSpec>(objectiveCCompiler, getOutputFileSuffix());
    }

    public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
        CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(ToolType.ASSEMBLER);
        return (Compiler<T>) new Assembler(commandLineTool, toolRegistry.getTool(ToolType.ASSEMBLER).getArgAction(), getOutputFileSuffix());
    }

    public <T extends BinaryToolSpec> Compiler<T> createWindowsResourceCompiler() {
        throw new UnsupportedOperationException();
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(ToolType.LINKER);
        return (Compiler<T>) new GccLinker(commandLineTool, toolRegistry.getTool(ToolType.LINKER).getArgAction(), useCommandFile);
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(ToolType.STATIC_LIB_ARCHIVER);
            return (Compiler<T>) new ArStaticLibraryArchiver(commandLineTool, toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER).getArgAction());
    }


    private String getOutputFileSuffix() {
        return OperatingSystem.current().isWindows() ? ".obj" : ".o";
    }

    private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(ToolType key) {
        String exeName = toolRegistry.getTool(key).getExecutable();
        CommandLineTool<T> commandLineTool = new CommandLineTool<T>(key.getToolName(), toolSearchPath.locate(key, exeName).getTool(), execActionFactory);
        // MinGW requires the path to be set
        commandLineTool.withPath(toolSearchPath.getPath());
        commandLineTool.withEnvironmentVar("CYGWIN", "nodosfilewarning");
        return commandLineTool;
    }

}
