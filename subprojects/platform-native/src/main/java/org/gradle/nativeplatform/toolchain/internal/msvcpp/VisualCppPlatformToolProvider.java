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

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.*;
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

class VisualCppPlatformToolProvider extends AbstractPlatformToolProvider {
    private final Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations;
    private final VisualCppInstall visualCpp;
    private final WindowsSdk sdk;
    private final Ucrt ucrt;
    private final NativePlatformInternal targetPlatform;
    private final ExecActionFactory execActionFactory;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;

    VisualCppPlatformToolProvider(BuildOperationProcessor buildOperationProcessor, OperatingSystemInternal operatingSystem, Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations, VisualCppInstall visualCpp, WindowsSdk sdk, Ucrt ucrt, NativePlatformInternal targetPlatform, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory) {
        super(buildOperationProcessor, operatingSystem);
        this.commandLineToolConfigurations = commandLineToolConfigurations;
        this.visualCpp = visualCpp;
        this.sdk = sdk;
        this.ucrt = ucrt;
        this.targetPlatform = targetPlatform;
        this.execActionFactory = execActionFactory;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    @Override
    protected Compiler<CppCompileSpec> createCppCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C++ compiler", visualCpp.getCompiler(targetPlatform));
        CppCompiler cppCompiler = new CppCompiler(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), addIncludePathAndDefinitions(CppCompileSpec.class), getObjectFileExtension(), true);
        return new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
    }

    @Override
    protected Compiler<?> createCppPCHCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C++ PCH compiler", visualCpp.getCompiler(targetPlatform));
        CppPCHCompiler cppPCHCompiler = new CppPCHCompiler(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), pchSpecTransforms(CppPCHCompileSpec.class), getPCHFileExtension(), true);
        return new OutputCleaningCompiler<CppPCHCompileSpec>(cppPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
    }

    @Override
    protected Compiler<CCompileSpec> createCCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C compiler", visualCpp.getCompiler(targetPlatform));
        CCompiler cCompiler = new CCompiler(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.C_COMPILER)), addIncludePathAndDefinitions(CCompileSpec.class), getObjectFileExtension(), true);
        return new OutputCleaningCompiler<CCompileSpec>(cCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
    }

    @Override
    protected Compiler<?> createCPCHCompiler() {
        CommandLineToolInvocationWorker commandLineTool = tool("C PCH compiler", visualCpp.getCompiler(targetPlatform));
        CPCHCompiler cpchCompiler = new CPCHCompiler(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.C_COMPILER)), pchSpecTransforms(CPCHCompileSpec.class), getPCHFileExtension(), true);
        return new OutputCleaningCompiler<CPCHCompileSpec>(cpchCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension());
    }

    @Override
    protected Compiler<AssembleSpec> createAssembler() {
        CommandLineToolInvocationWorker commandLineTool = tool("Assembler", visualCpp.getAssembler(targetPlatform));
        return new Assembler(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.ASSEMBLER)), addIncludePathAndDefinitions(AssembleSpec.class), getObjectFileExtension(), false);
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
        WindowsResourceCompiler windowsResourceCompiler = new WindowsResourceCompiler(buildOperationProcessor, compilerOutputFileNamingSchemeFactory, commandLineTool, context(commandLineToolConfigurations.get(ToolType.WINDOW_RESOURCES_COMPILER)), addIncludePathAndDefinitions(WindowsResourceCompileSpec.class), objectFileExtension, false);
        return new OutputCleaningCompiler<WindowsResourceCompileSpec>(windowsResourceCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        CommandLineToolInvocationWorker commandLineTool = tool("Linker", visualCpp.getLinker(targetPlatform));
        return new LinkExeLinker(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.LINKER)), addLibraryPath());
    }

    @Override
    protected Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        CommandLineToolInvocationWorker commandLineTool = tool("Static library archiver", visualCpp.getArchiver(targetPlatform));
        return new LibExeStaticLibraryArchiver(buildOperationProcessor, commandLineTool, context(commandLineToolConfigurations.get(ToolType.STATIC_LIB_ARCHIVER)), Transformers.<StaticLibraryArchiverSpec>noOpTransformer());
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
                transformers.add(addIncludePathAndDefinitions(type));

                T next = original;
                for (Transformer<T, T> transformer :  transformers) {
                    next = transformer.transform(next);
                }
                return next;
            }
        };
    }

    private <T extends NativeCompileSpec> Transformer<T, T> addIncludePathAndDefinitions(Class<T> type) {
        return new Transformer<T, T>() {
            public T transform(T original) {
                original.include(visualCpp.getIncludePath(targetPlatform));
                original.include(sdk.getIncludeDirs());
                if (ucrt != null) {
                    original.include(ucrt.getIncludeDirs());
                }
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
