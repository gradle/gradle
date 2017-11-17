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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.DefaultCompilerVersion;
import org.gradle.language.base.internal.compile.VersionAwareCompiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler;
import org.gradle.nativeplatform.toolchain.internal.PCHUtils;
import org.gradle.nativeplatform.toolchain.internal.SystemIncludesAwarePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

class VisualCppPlatformToolProvider extends AbstractPlatformToolProvider implements SystemIncludesAwarePlatformToolProvider {
    private final Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations;
    private final VisualCppInstall visualCpp;
    private final WindowsSdk sdk;
    private final Ucrt ucrt;
    private final NativePlatformInternal targetPlatform;
    private final ExecActionFactory execActionFactory;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final WorkerLeaseService workerLeaseService;

    VisualCppPlatformToolProvider(BuildOperationExecutor buildOperationExecutor, OperatingSystemInternal operatingSystem, Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations, VisualCppInstall visualCpp, WindowsSdk sdk, Ucrt ucrt, NativePlatformInternal targetPlatform, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, WorkerLeaseService workerLeaseService) {
        super(buildOperationExecutor, operatingSystem);
        this.commandLineToolConfigurations = commandLineToolConfigurations;
        this.visualCpp = visualCpp;
        this.sdk = sdk;
        this.ucrt = ucrt;
        this.targetPlatform = targetPlatform;
        this.execActionFactory = execActionFactory;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public boolean producesImportLibrary() {
        return true;
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    @Override
    protected Compiler<CppCompileSpec> createCppCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C++ compiler", visualCpp.getCompiler(targetPlatform));
        CppCompiler cppCompiler = new CppCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), addDefinitions(CppCompileSpec.class), getObjectFileExtension(), true, workerLeaseService);
        OutputCleaningCompiler<CppCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<?> createCppPCHCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C++ PCH compiler", visualCpp.getCompiler(targetPlatform));
        CppPCHCompiler cppPCHCompiler = new CppPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), pchSpecTransforms(CppPCHCompileSpec.class), getPCHFileExtension(), true, workerLeaseService);
        OutputCleaningCompiler<CppPCHCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CppPCHCompileSpec>(cppPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<CCompileSpec> createCCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C compiler", visualCpp.getCompiler(targetPlatform));
        CCompiler cCompiler = new CCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.C_COMPILER)), addDefinitions(CCompileSpec.class), getObjectFileExtension(), true, workerLeaseService);
        OutputCleaningCompiler<CCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CCompileSpec>(cCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    @Override
    protected Compiler<?> createCPCHCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C PCH compiler", visualCpp.getCompiler(targetPlatform));
        CPCHCompiler cpchCompiler = new CPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.C_COMPILER)), pchSpecTransforms(CPCHCompileSpec.class), getPCHFileExtension(), true, workerLeaseService);
        OutputCleaningCompiler<CPCHCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<CPCHCompileSpec>(cpchCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
        return versionAwareCompiler(outputCleaningCompiler);
    }

    private <T extends NativeCompileSpec> VersionAwareCompiler<T> versionAwareCompiler(OutputCleaningCompiler<T> outputCleaningCompiler) {
        return new VersionAwareCompiler<T>(outputCleaningCompiler, new DefaultCompilerVersion(VisualCppToolChain.DEFAULT_NAME, "Microsoft", visualCpp.getVersion()));
    }

    @Override
    protected Compiler<AssembleSpec> createAssembler() {
        CommandLineToolInvocationWorker commandLineTool = tool("Assembler", visualCpp.getAssembler(targetPlatform));
        return new Assembler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.ASSEMBLER)), addDefinitions(AssembleSpec.class), getObjectFileExtension(), false, workerLeaseService);
    }

    @Override
    protected Compiler<?> createObjectiveCppCompiler() {
        throw unavailableTool("Objective-C++ is not available on the Visual C++ toolchain");
    }

    @Override
    protected Compiler<?> createObjectiveCCompiler() {
        throw unavailableTool("Objective-C is not available on the Visual C++ toolchain");
    }

    @Override
    protected Compiler<WindowsResourceCompileSpec> createWindowsResourceCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("Windows resource compiler", sdk.getResourceCompiler(targetPlatform));
        String objectFileExtension = ".res";
        WindowsResourceCompiler windowsResourceCompiler = new WindowsResourceCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.WINDOW_RESOURCES_COMPILER)), addDefinitions(WindowsResourceCompileSpec.class), objectFileExtension, false, workerLeaseService);
        return new OutputCleaningCompiler<WindowsResourceCompileSpec>(windowsResourceCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        CommandLineToolInvocationWorker commandLineTool = tool("Linker", visualCpp.getLinker(targetPlatform));
        return new LinkExeLinker(buildOperationExecutor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.LINKER)), addLibraryPath(), workerLeaseService);
    }

    @Override
    protected Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        CommandLineToolInvocationWorker commandLineTool = tool("Static library archiver", visualCpp.getArchiver(targetPlatform));
        return new LibExeStaticLibraryArchiver(buildOperationExecutor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.STATIC_LIB_ARCHIVER)), Transformers.<StaticLibraryArchiverSpec>noOpTransformer(), workerLeaseService);
    }

    private CommandLineToolInvocationWorker tool(String toolName, File exe) {
        return new DefaultCommandLineToolInvocationWorker(toolName, exe, execActionFactory);
    }

    private CommandLineToolContext context(CommandLineToolConfigurationInternal commandLineToolConfiguration) {
        MutableCommandLineToolContext invocationContext = new DefaultMutableCommandLineToolContext();
        // The visual C++ tools use the path to find other executables
        // TODO:ADAM - restrict this to the specific path for the target tool
        invocationContext.addPath(visualCpp.getPath(targetPlatform));
        invocationContext.addPath(sdk.getBinDir(targetPlatform));
        // Clear environment variables that might effect cl.exe & link.exe
        clearEnvironmentVars(invocationContext, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");

        invocationContext.setArgAction(commandLineToolConfiguration.getArgAction());
        return invocationContext;
    }

    private void clearEnvironmentVars(MutableCommandLineToolContext invocation, String... names) {
        // TODO: This check should really be done in the compiler process
        Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
        for (String name : names) {
            Object value = environmentVariables.get(name);
            if (value != null) {
                VisualCppToolChain.LOGGER.warn("Ignoring value '{}' set for environment variable '{}'.", value, name);
                invocation.addEnvironmentVar(name, "");
            }
        }
    }

    private <T extends NativeCompileSpec> Transformer<T, T> pchSpecTransforms(final Class<T> type) {
        return new Transformer<T, T>() {
            @Override
            public T transform(T original) {
                List<Transformer<T, T>> transformers = Lists.newArrayList();
                transformers.add(PCHUtils.getHeaderToSourceFileTransformer(type));
                transformers.add(addDefinitions(type));

                T next = original;
                for (Transformer<T, T> transformer :  transformers) {
                    next = transformer.transform(next);
                }
                return next;
            }
        };
    }

    @Override
    public List<File> getSystemIncludes() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        builder.add(visualCpp.getIncludePath(targetPlatform));
        builder.add(sdk.getIncludeDirs());
        if (ucrt != null) {
            builder.add(ucrt.getIncludeDirs());
        }

        return builder.build();
    }

    private <T extends NativeCompileSpec> Transformer<T, T> addDefinitions(Class<T> type) {
        return new Transformer<T, T>() {
            public T transform(T original) {
                for (Map.Entry<String, String> definition : visualCpp.getDefinitions(targetPlatform).entrySet()) {
                    original.define(definition.getKey(), definition.getValue());
                }
                return original;
            }
        };
    }

    private Transformer<LinkerSpec, LinkerSpec> addLibraryPath() {
        return new Transformer<LinkerSpec, LinkerSpec>() {
            public LinkerSpec transform(LinkerSpec original) {
                if (ucrt == null) {
                    original.libraryPath(visualCpp.getLibraryPath(targetPlatform), sdk.getLibDir(targetPlatform));
                } else {
                    original.libraryPath(visualCpp.getLibraryPath(targetPlatform), sdk.getLibDir(targetPlatform), ucrt.getLibDir(targetPlatform));
                }
                return original;
            }
        };
    }

    public String getPCHFileExtension() {
        return ".pch";
    }
}
