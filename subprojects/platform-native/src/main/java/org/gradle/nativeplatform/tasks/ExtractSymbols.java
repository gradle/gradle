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
import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
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

/**
 * Extracts the debug symbols from a binary and stores them in a separate file.
 *
 * @since 4.5
 */
@Incubating
public class ExtractSymbols extends DefaultTask {
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private RegularFileProperty binaryFile;
    private RegularFileProperty symbolFile;

    public ExtractSymbols() {
        this.binaryFile = newInputFile();
        this.symbolFile = newOutputFile();
    }

    /**
     * The file to extract debug symbols from.
     */
    @InputFile
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

    @Internal
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = (NativeToolChainInternal) toolChain;
    }

    @Nested
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = (NativePlatformInternal) targetPlatform;
    }

    @TaskAction
    public void extractSymbols() {
        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        SymbolExtractorSpec spec = new DefaultSymbolExtractorSpec();
        spec.setBinaryFile(binaryFile.get().getAsFile());
        spec.setSymbolFile(symbolFile.get().getAsFile());
        spec.setOperationLogger(operationLogger);

        Compiler<SymbolExtractorSpec> symbolExtractor = Cast.uncheckedCast(toolChain.select(targetPlatform).newCompiler(spec.getClass()));
        symbolExtractor = BuildOperationLoggingCompilerDecorator.wrap(symbolExtractor);
        WorkResult result = symbolExtractor.execute(spec);
        setDidWork(result.getDidWork());
    }
}
