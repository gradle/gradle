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

import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.language.base.internal.compile.DefaultCompilerVersion;
import org.gradle.language.base.internal.compile.VersionAwareCompiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.SwiftcPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.VersionNumber;

class SwiftPlatformToolProvider extends AbstractPlatformToolProvider {
    private final ToolSearchPath toolSearchPath;
    private final SwiftcPlatformToolChain toolRegistry;
    private final ExecActionFactory execActionFactory;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final WorkerLeaseService workerLeaseService;
    private final SwiftcMetadata swiftcMetaData;

    SwiftPlatformToolProvider(BuildOperationExecutor buildOperationExecutor, OperatingSystemInternal targetOperatingSystem, ToolSearchPath toolSearchPath, SwiftcPlatformToolChain toolRegistry, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, WorkerLeaseService workerLeaseService, SwiftcMetadata swiftcMetaData) {
        super(buildOperationExecutor, targetOperatingSystem);
        this.toolRegistry = toolRegistry;
        this.toolSearchPath = toolSearchPath;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.execActionFactory = execActionFactory;
        this.workerLeaseService = workerLeaseService;
        this.swiftcMetaData = swiftcMetaData;
    }

    @Override
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        if (SwiftCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createSwiftCompiler());
        }
        return super.newCompiler(spec);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        CommandLineToolConfigurationInternal linkerTool = (CommandLineToolConfigurationInternal) toolRegistry.getLinker();
        return new SwiftLinker(buildOperationExecutor, commandLineTool(ToolType.LINKER, "swiftc"), context(linkerTool), workerLeaseService);
    }

    protected Compiler<SwiftCompileSpec> createSwiftCompiler() {
        CommandLineToolConfigurationInternal swiftCompilerTool = (CommandLineToolConfigurationInternal) toolRegistry.getSwiftCompiler();
        SwiftCompiler swiftCompiler = new SwiftCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(ToolType.SWIFT_COMPILER, "swiftc"), context(swiftCompilerTool), getObjectFileExtension(), workerLeaseService);
        // TODO - OutputCleaningCompiler shouldn't be required
        OutputCleaningCompiler<SwiftCompileSpec> outputCleaningCompiler = new OutputCleaningCompiler<SwiftCompileSpec>(swiftCompiler, compilerOutputFileNamingSchemeFactory, getObjectFileExtension());
        return new VersionAwareCompiler<SwiftCompileSpec>(outputCleaningCompiler, new DefaultCompilerVersion("swiftc", swiftcMetaData.getVendor(), VersionNumber.UNKNOWN));
    }

    private CommandLineToolInvocationWorker commandLineTool(ToolType key, String exeName) {
        return new DefaultCommandLineToolInvocationWorker(key.getToolName(), toolSearchPath.locate(key, exeName).getTool(), execActionFactory);
    }

    private CommandLineToolContext context(CommandLineToolConfigurationInternal toolConfiguration) {
        MutableCommandLineToolContext baseInvocation = new DefaultMutableCommandLineToolContext();
        baseInvocation.setArgAction(toolConfiguration.getArgAction());
        return baseInvocation;
    }
}
