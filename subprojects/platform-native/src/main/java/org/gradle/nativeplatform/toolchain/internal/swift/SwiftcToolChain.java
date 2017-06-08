/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.swift;

import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.Swiftc;
import org.gradle.nativeplatform.toolchain.SwiftcPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.List;

public class SwiftcToolChain extends ExtendableToolChain<SwiftcPlatformToolChain> implements Swiftc, NativeToolChainInternal {
    public static final String DEFAULT_NAME = "swiftc";

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final Instantiator instantiator;
    private final ToolSearchPath toolSearchPath;
    private final ExecActionFactory execActionFactory;

    public SwiftcToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, PathToFileResolver fileResolver, ExecActionFactory execActionFactory,
                           CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProviderFactory compilerMetaDataProviderFactory, Instantiator instantiator) {
        this(name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, new ToolSearchPath(operatingSystem), compilerMetaDataProviderFactory, instantiator);
    }

    SwiftcToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, PathToFileResolver fileResolver, ExecActionFactory execActionFactory,
                    CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, ToolSearchPath tools, CompilerMetaDataProviderFactory compilerMetaDataProviderFactory, Instantiator instantiator) {
        super(name, buildOperationExecutor, operatingSystem, fileResolver);
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.instantiator = instantiator;
        this.toolSearchPath = tools;
        this.execActionFactory = execActionFactory;
    }

    @Override
    public List<File> getPath() {
        return toolSearchPath.getPath();
    }

    @Override
    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            toolSearchPath.path(resolve(path));
        }
    }

    @Override
    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        return new AbstractPlatformToolProvider(buildOperationExecutor, targetPlatform.getOperatingSystem()) {
            @Override
            public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
                if (SwiftCompileSpec.class.isAssignableFrom(spec)) {
                    return CompilerUtil.castCompiler(createSwiftCompiler());
                }
                return super.newCompiler(spec);
            }

            protected Compiler<SwiftCompileSpec> createSwiftCompiler() {
                GccCommandLineToolConfigurationInternal swiftCompilerTool = instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.SWIFT_COMPILER, "swiftc");
                SwiftCompiler swiftCompiler = new SwiftCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(swiftCompilerTool), context(swiftCompilerTool), getObjectFileExtension(), false);
                return new OutputCleaningCompiler<SwiftCompileSpec>(swiftCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
            }

            private CommandLineToolInvocationWorker commandLineTool(GccCommandLineToolConfigurationInternal tool) {
                ToolType key = tool.getToolType();
                String exeName = tool.getExecutable();
                return new DefaultCommandLineToolInvocationWorker(key.getToolName(), toolSearchPath.locate(key, exeName).getTool(), execActionFactory);
            }

            private CommandLineToolContext context(GccCommandLineToolConfigurationInternal toolConfiguration) {
                MutableCommandLineToolContext baseInvocation = new DefaultMutableCommandLineToolContext();
                // MinGW requires the path to be set
//                    baseInvocation.addPath(toolSearchPath.getPath());
//                baseInvocation.addEnvironmentVar("CYGWIN", "nodosfilewarning");
                baseInvocation.setArgAction(toolConfiguration.getArgAction());
                return baseInvocation;
            }
        };
    }

    @Override
    protected String getTypeName() {
        return "Swift Compiler";
    }
}
