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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.compilespec.*;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

class GccPlatformToolProvider implements PlatformToolProvider {
    private final ToolSearchPath toolSearchPath;
    private final OperatingSystemInternal targetOperatingSystem;
    private final ToolRegistry toolRegistry;
    private final ExecActionFactory execActionFactory;
    private final boolean useCommandFile;
    private final String outputFileSuffix;

    GccPlatformToolProvider(OperatingSystemInternal targetOperatingSystem, ToolSearchPath toolSearchPath, ToolRegistry toolRegistry, ExecActionFactory execActionFactory, boolean useCommandFile) {
        this.targetOperatingSystem = targetOperatingSystem;
        this.toolRegistry = toolRegistry;
        this.toolSearchPath = toolSearchPath;
        this.execActionFactory = execActionFactory;
        this.useCommandFile = useCommandFile;
        this.outputFileSuffix = "." + getObjectFileExtension();
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    public String getObjectFileExtension() {
        return targetOperatingSystem.isWindows() ? "obj" : "o";
    }

    public String getExecutableName(String executablePath) {
        return targetOperatingSystem.getInternalOs().getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getSharedLibraryLinkFileName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getStaticLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getStaticLibraryName(libraryPath);
    }

    public <T extends CompileSpec> Compiler<T> newCompiler(T spec) {
        if (spec instanceof CppCompileSpec) {
            return castCompiler(createCppCompiler());
        }
        if (spec instanceof CCompileSpec) {
            return castCompiler(createCCompiler());
        }
        if (spec instanceof ObjectiveCppCompileSpec) {
            return castCompiler(createObjectiveCppCompiler());
        }
        if (spec instanceof ObjectiveCCompileSpec) {
            return castCompiler(createObjectiveCCompiler());
        }
        if (spec instanceof WindowsResourceCompileSpec) {
            throw new RuntimeException("Windows resource compiler is not available");
        }
        if (spec instanceof AssembleSpec) {
            return castCompiler(createAssembler());
        }
        if (spec instanceof LinkerSpec) {
            return castCompiler(createLinker());
        }
        if (spec instanceof StaticLibraryArchiverSpec) {
            return castCompiler(createStaticLibraryArchiver());
        }
        throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    private <T extends CompileSpec> Compiler<T> castCompiler(Compiler<?> compiler) {
        return (Compiler<T>) compiler;
    }

    public Compiler<CppCompileSpec> createCppCompiler() {
        GccCommandLineToolConfigurationInternal cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER);
        CppCompiler cppCompiler = new CppCompiler(commandLineTool(cppCompilerTool), commandLineToolInvocation(cppCompilerTool), outputFileSuffix, useCommandFile);
        return new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, outputFileSuffix);
    }

    public Compiler<CCompileSpec> createCCompiler() {
        GccCommandLineToolConfigurationInternal cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER);
        CCompiler cCompiler = new CCompiler(commandLineTool(cCompilerTool), commandLineToolInvocation(cCompilerTool), outputFileSuffix, useCommandFile);
        return new OutputCleaningCompiler<CCompileSpec>(cCompiler, outputFileSuffix);
    }

    public Compiler<ObjectiveCppCompileSpec> createObjectiveCppCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCppCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER);
        ObjectiveCppCompiler objectiveCppCompiler = new ObjectiveCppCompiler(commandLineTool(objectiveCppCompilerTool), commandLineToolInvocation(objectiveCppCompilerTool), outputFileSuffix, useCommandFile);
        return new OutputCleaningCompiler<ObjectiveCppCompileSpec>(objectiveCppCompiler, outputFileSuffix);
    }

    public Compiler<ObjectiveCCompileSpec> createObjectiveCCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER);
        ObjectiveCCompiler objectiveCCompiler = new ObjectiveCCompiler(commandLineTool(objectiveCCompilerTool), commandLineToolInvocation(objectiveCCompilerTool), outputFileSuffix, useCommandFile);
        return new OutputCleaningCompiler<ObjectiveCCompileSpec>(objectiveCCompiler, outputFileSuffix);
    }

    public Compiler<AssembleSpec> createAssembler() {
        GccCommandLineToolConfigurationInternal assemblerTool = toolRegistry.getTool(ToolType.ASSEMBLER);
        return new Assembler(commandLineTool(assemblerTool), commandLineToolInvocation(assemblerTool), outputFileSuffix);
    }

    public Compiler<LinkerSpec> createLinker() {
        GccCommandLineToolConfigurationInternal linkerTool = toolRegistry.getTool(ToolType.LINKER);
        return new GccLinker(commandLineTool(linkerTool), commandLineToolInvocation(linkerTool), useCommandFile);
    }

    public Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        GccCommandLineToolConfigurationInternal staticLibArchiverTool = toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER);
        return new ArStaticLibraryArchiver(commandLineTool(staticLibArchiverTool), commandLineToolInvocation(staticLibArchiverTool));
    }

    private CommandLineTool commandLineTool(GccCommandLineToolConfigurationInternal tool) {
        ToolType key = tool.getToolType();
        String exeName = tool.getExecutable();
        return new CommandLineTool(key.getToolName(), toolSearchPath.locate(key, exeName).getTool(), execActionFactory);
    }

    private CommandLineToolInvocation commandLineToolInvocation(GccCommandLineToolConfigurationInternal toolConfiguration) {
        MutableCommandLineToolInvocation baseInvocation = new DefaultCommandLineToolInvocation();
        // MinGW requires the path to be set
        baseInvocation.addPath(toolSearchPath.getPath());
        baseInvocation.addEnvironmentVar("CYGWIN", "nodosfilewarning");

        baseInvocation.addPostArgsAction(toolConfiguration.getArgAction());
        return baseInvocation;
    }
}
