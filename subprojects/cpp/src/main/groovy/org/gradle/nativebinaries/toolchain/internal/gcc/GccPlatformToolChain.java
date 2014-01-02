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

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.toolchain.internal.PlatformToolChain;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.language.objectivec.internal.ObjectiveCCompileSpec;
import org.gradle.nativebinaries.language.objectivecpp.internal.ObjectiveCppCompileSpec;
import org.gradle.nativebinaries.toolchain.TargetPlatformConfiguration;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.OutputCleaningCompiler;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.process.internal.ExecActionFactory;

import java.util.List;

class GccPlatformToolChain implements PlatformToolChain {
    private final ToolRegistry tools;
    private final ExecActionFactory execActionFactory;
    private final boolean useCommandFile;
    private final TargetPlatformConfiguration platformConfiguration;

    GccPlatformToolChain(ToolRegistry tools, ExecActionFactory execActionFactory, TargetPlatformConfiguration platformConfiguration, boolean useCommandFile) {
        this.execActionFactory = execActionFactory;
        this.tools = tools;
        this.platformConfiguration = platformConfiguration;
        this.useCommandFile = useCommandFile;
    }

    public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
        CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(ToolType.CPP_COMPILER);
        commandLineTool.withSpecTransformer(withSystemArgs(CppCompileSpec.class, platformConfiguration.getCppCompilerArgs()));
        CppCompiler cppCompiler = new CppCompiler(commandLineTool, tools.getArgTransformer(ToolType.CPP_COMPILER), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, ".o");
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(ToolType.C_COMPILER);
        commandLineTool.withSpecTransformer(withSystemArgs(CCompileSpec.class, platformConfiguration.getCCompilerArgs()));
        CCompiler cCompiler = new CCompiler(commandLineTool, tools.getArgTransformer(ToolType.C_COMPILER), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<CCompileSpec>(cCompiler, ".o");
    }

    public <T extends BinaryToolSpec> Compiler<T> createObjectiveCppCompiler() {
        CommandLineTool<ObjectiveCppCompileSpec> commandLineTool = commandLineTool(ToolType.OBJECTIVECPP_COMPILER);
        commandLineTool.withSpecTransformer(withSystemArgs(ObjectiveCppCompileSpec.class, platformConfiguration.getObjectiveCppCompilerArgs()));
        ObjectiveCppCompiler objectiveCppCompiler = new ObjectiveCppCompiler(commandLineTool, tools.getArgTransformer(ToolType.OBJECTIVECPP_COMPILER), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<ObjectiveCppCompileSpec>(objectiveCppCompiler, ".o");
    }

    public <T extends BinaryToolSpec> Compiler<T> createObjectiveCCompiler() {
        CommandLineTool<ObjectiveCCompileSpec> commandLineTool = commandLineTool(ToolType.OBJECTIVEC_COMPILER);
        commandLineTool.withSpecTransformer(withSystemArgs(ObjectiveCCompileSpec.class, platformConfiguration.getObjectiveCCompilerArgs()));
        ObjectiveCCompiler objectiveCCompiler = new ObjectiveCCompiler(commandLineTool, tools.getArgTransformer(ToolType.OBJECTIVEC_COMPILER), useCommandFile);
        return (Compiler<T>) new OutputCleaningCompiler<ObjectiveCCompileSpec>(objectiveCCompiler, ".o");
    }

    public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
        CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(ToolType.ASSEMBLER);
        commandLineTool.withSpecTransformer(withSystemArgs(AssembleSpec.class, platformConfiguration.getAssemblerArgs()));
        return (Compiler<T>) new Assembler(commandLineTool, tools.getArgTransformer(ToolType.ASSEMBLER));
    }

    public <T extends BinaryToolSpec> Compiler<T> createWindowsResourceCompiler() {
        throw new UnsupportedOperationException();
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(ToolType.LINKER);
        commandLineTool.withSpecTransformer(withSystemArgs(LinkerSpec.class, platformConfiguration.getLinkerArgs()));
        return (Compiler<T>) new GccLinker(commandLineTool, tools.getArgTransformer(ToolType.LINKER), useCommandFile);
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(ToolType.STATIC_LIB_ARCHIVER);
        commandLineTool.withSpecTransformer(withSystemArgs(StaticLibraryArchiverSpec.class, platformConfiguration.getStaticLibraryArchiverArgs()));
        return (Compiler<T>) new ArStaticLibraryArchiver(commandLineTool, tools.getArgTransformer(ToolType.STATIC_LIB_ARCHIVER));
    }

    private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(ToolType key) {
        CommandLineTool<T> commandLineTool = new CommandLineTool<T>(key.getToolName(), tools.locate(key).getTool(), execActionFactory);
        // MinGW requires the path to be set
        commandLineTool.withPath(tools.getPath());
        commandLineTool.withEnvironmentVar("CYGWIN", "nodosfilewarning");
        return commandLineTool;
    }

    private <T extends BinaryToolSpec> Transformer<T, T> withSystemArgs(Class<T> specType, final List<String> args) {
        return new Transformer<T, T>() {
            public T transform(T original) {
                original.systemArgs(args);
                return original;
            }
        };
    }

}
