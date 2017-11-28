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
import org.gradle.nativeplatform.internal.DefaultStripperSpec;
import org.gradle.nativeplatform.internal.StripperSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;

/**
 * Strips the debug symbols from a binary
 *
 * @since 4.5
 */
@Incubating
public class StripSymbols extends DefaultTask {
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private RegularFileProperty binaryFile;
    private RegularFileProperty outputFile;

    public StripSymbols() {
        this.binaryFile = newInputFile();
        this.outputFile = newOutputFile();
    }

    /**
     * The file that debug symbols should be stripped from.  Note that this file remains unchanged
     * and a new stripped binary will be written to the file specified by {{@link #getOutputFile()}}.
     */
    @InputFile
    public RegularFileProperty getBinaryFile() {
        return binaryFile;
    }

    /**
     * The destination to write the stripped binary to.
     */
    @OutputFile
    public RegularFileProperty getOutputFile() {
        return outputFile;
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
    public void stripSymbols() {
        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        StripperSpec spec = new DefaultStripperSpec();
        spec.setBinaryFile(binaryFile.get().getAsFile());
        spec.setOutputFile(outputFile.get().getAsFile());
        spec.setOperationLogger(operationLogger);

        Compiler<StripperSpec> symbolStripper = Cast.uncheckedCast(toolChain.select(targetPlatform).newCompiler(spec.getClass()));
        symbolStripper = BuildOperationLoggingCompilerDecorator.wrap(symbolStripper);
        WorkResult result = symbolStripper.execute(spec);
        setDidWork(result.getDidWork());
    }
}
