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

package org.gradle.nativeplatform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.DefaultSymbolExtractorSpec;
import org.gradle.nativeplatform.internal.SymbolExtractorSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

/**
 * Extracts the debug symbols from a binary and stores them in a separate file.
 *
 * @since 4.5
 */
public class ExtractSymbols extends DefaultTask {
    private final RegularFileProperty binaryFile;
    private final RegularFileProperty symbolFile;
    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;

    public ExtractSymbols() {
        ObjectFactory objectFactory = getProject().getObjects();

        this.binaryFile = objectFactory.fileProperty();
        this.symbolFile = objectFactory.fileProperty();
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
    }

    /**
     * The file to extract debug symbols from.
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public RegularFileProperty getBinaryFile() {
        return binaryFile;
    }

    /**
     * The destination file to extract debug symbols into.
     */
    @OutputFile
    public RegularFileProperty getSymbolFile() {
        return symbolFile;
    }

    /**
     * The tool chain used for extracting symbols.
     *
     * @since 4.7
     */
    @Internal
    public Property<NativeToolChain> getToolChain() {
        return toolChain;
    }

    /**
     * The platform for the binary.
     *
     * @since 4.7
     */
    @Nested
    public Property<NativePlatform> getTargetPlatform() {
        return targetPlatform;
    }

    // TODO: Need to track version/implementation of symbol extraction tool.

    @TaskAction
    protected void extractSymbols() {
        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        SymbolExtractorSpec spec = new DefaultSymbolExtractorSpec();
        spec.setBinaryFile(binaryFile.get().getAsFile());
        spec.setSymbolFile(symbolFile.get().getAsFile());
        spec.setOperationLogger(operationLogger);

        Compiler<SymbolExtractorSpec> symbolExtractor = createCompiler();
        symbolExtractor = BuildOperationLoggingCompilerDecorator.wrap(symbolExtractor);
        WorkResult result = symbolExtractor.execute(spec);
        setDidWork(result.getDidWork());
    }

    private Compiler<SymbolExtractorSpec> createCompiler() {
        NativePlatformInternal targetPlatform = Cast.cast(NativePlatformInternal.class, this.targetPlatform.get());
        NativeToolChainInternal toolChain = Cast.cast(NativeToolChainInternal.class, getToolChain().get());
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        return toolProvider.newCompiler(SymbolExtractorSpec.class);
    }
}
