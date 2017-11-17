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

import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.DefaultCompilerVersion;
import org.gradle.language.base.internal.compile.VersionAwareCompiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler;
import org.gradle.nativeplatform.toolchain.internal.SystemIncludesAwarePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.List;

class GccPlatformToolProvider extends AbstractPlatformToolProvider implements SystemIncludesAwarePlatformToolProvider {
    private final ToolSearchPath toolSearchPath;
    private final ToolRegistry toolRegistry;
    private final ExecActionFactory execActionFactory;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final boolean useCommandFile;
    private final WorkerLeaseService workerLeaseService;
    private final CompilerType compilerType;
    private final GccMetadata gccMetadata;

    GccPlatformToolProvider(BuildOperationExecutor buildOperationExecutor, OperatingSystemInternal targetOperatingSystem, ToolSearchPath toolSearchPath, ToolRegistry toolRegistry, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, boolean useCommandFile, WorkerLeaseService workerLeaseService, CompilerType compilerType, GccMetadata gccMetadata) {
        super(buildOperationExecutor, targetOperatingSystem);
        this.toolRegistry = toolRegistry;
        this.toolSearchPath = toolSearchPath;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.useCommandFile = useCommandFile;
        this.execActionFactory = execActionFactory;
        this.workerLeaseService = workerLeaseService;
        this.compilerType = compilerType;
        this.gccMetadata = gccMetadata;
    }

    @Override
    protected Compiler<CppCompileSpec> createCppCompiler() {
        GccCommandLineToolConfigurationInternal cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER);
        CppCompiler cppCompiler = new CppCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cppCompilerTool), context(cppCompilerTool), getObjectFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<CppCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<?> createCppPCHCompiler() {
        GccCommandLineToolConfigurationInternal cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER);
        CppPCHCompiler cppPCHCompiler = new CppPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cppCompilerTool), context(cppCompilerTool), getPCHFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<CppPCHCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CppPCHCompileSpec>(cppPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    private <T extends NativeCompileSpec> VersionAwareCompiler<T> versionAwareCompiler(Compiler<T> compiler) {
        return new VersionAwareCompiler<T>(compiler, new DefaultCompilerVersion(compilerType.getIdentifier(), gccMetadata.getVendor(), gccMetadata.getVersion()));
    }

    @Override
    protected Compiler<CCompileSpec> createCCompiler() {
        GccCommandLineToolConfigurationInternal cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER);
        CCompiler cCompiler = new CCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cCompilerTool), context(cCompilerTool), getObjectFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<CCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CCompileSpec>(cCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<?> createCPCHCompiler() {
        GccCommandLineToolConfigurationInternal cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER);
        CPCHCompiler cpchCompiler = new CPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cCompilerTool), context(cCompilerTool), getPCHFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<CPCHCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CPCHCompileSpec>(cpchCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<ObjectiveCppCompileSpec> createObjectiveCppCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCppCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER);
        ObjectiveCppCompiler objectiveCppCompiler = new ObjectiveCppCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(objectiveCppCompilerTool), context(objectiveCppCompilerTool), getObjectFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<ObjectiveCppCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<ObjectiveCppCompileSpec>(objectiveCppCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<?> createObjectiveCppPCHCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCppCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER);
        ObjectiveCppPCHCompiler objectiveCppPCHCompiler = new ObjectiveCppPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(objectiveCppCompilerTool), context(objectiveCppCompilerTool), getPCHFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<ObjectiveCppPCHCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<ObjectiveCppPCHCompileSpec>(objectiveCppPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<ObjectiveCCompileSpec> createObjectiveCCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER);
        ObjectiveCCompiler objectiveCCompiler = new ObjectiveCCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(objectiveCCompilerTool), context(objectiveCCompilerTool), getObjectFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<ObjectiveCCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<ObjectiveCCompileSpec>(objectiveCCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<?> createObjectiveCPCHCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER);
        ObjectiveCPCHCompiler objectiveCPCHCompiler = new ObjectiveCPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(objectiveCCompilerTool), context(objectiveCCompilerTool), getPCHFileExtension(), useCommandFile, workerLeaseService);
        OutputCleaningCompiler<ObjectiveCPCHCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<ObjectiveCPCHCompileSpec>(objectiveCPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<AssembleSpec> createAssembler() {
        GccCommandLineToolConfigurationInternal assemblerTool = toolRegistry.getTool(ToolType.ASSEMBLER);
        // Disable command line file for now because some custom assemblers
        // don't understand the same arguments as GCC.
        return new Assembler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(assemblerTool), context(assemblerTool), getObjectFileExtension(), false, workerLeaseService);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        GccCommandLineToolConfigurationInternal linkerTool = toolRegistry.getTool(ToolType.LINKER);
        return new GccLinker(buildOperationExecutor, commandLineTool(linkerTool), context(linkerTool), useCommandFile, workerLeaseService);
    }

    @Override
    protected Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        GccCommandLineToolConfigurationInternal staticLibArchiverTool = toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER);
        return new ArStaticLibraryArchiver(buildOperationExecutor, commandLineTool(staticLibArchiverTool), context(staticLibArchiverTool), workerLeaseService);
    }

    private CommandLineToolInvocationWorker commandLineTool(GccCommandLineToolConfigurationInternal tool) {
        ToolType key = tool.getToolType();
        String exeName = tool.getExecutable();
        return new DefaultCommandLineToolInvocationWorker(key.getToolName(), toolSearchPath.locate(key, exeName).getTool(), execActionFactory);
    }

    private CommandLineToolContext context(GccCommandLineToolConfigurationInternal toolConfiguration) {
        MutableCommandLineToolContext baseInvocation = new DefaultMutableCommandLineToolContext();
        // MinGW requires the path to be set
        baseInvocation.addPath(toolSearchPath.getPath());
        baseInvocation.addEnvironmentVar("CYGWIN", "nodosfilewarning");
        baseInvocation.setArgAction(toolConfiguration.getArgAction());
        return baseInvocation;
    }

    private String getPCHFileExtension() {
        return ".h.gch";
    }

    @Override
    public List<File> getSystemIncludes() {
        return gccMetadata.getSystemIncludes();
    }
}
