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
import org.gradle.nativeplatform.internal.DefaultStripperSpec;
import org.gradle.nativeplatform.internal.StripperSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.work.DisableCachingByDefault;

/**
 * Strips the debug symbols from a binary
 *
 * @since 4.5
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class StripSymbols extends DefaultTask {
    /**
     * The file that debug symbols should be stripped from.  Note that this file remains unchanged
     * and a new stripped binary will be written to the file specified by {{@link #getOutputFile()}}.
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBinaryFile();

    /**
     * The destination to write the stripped binary to.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * The tool chain used for striping symbols.
     *
     * @since 4.7
     */
    @Internal
    public abstract Property<NativeToolChain> getToolChain();

    /**
     * The platform for the binary.
     *
     * @since 4.7
     */
    @Nested
    public abstract Property<NativePlatform> getTargetPlatform();

    // TODO: Need to track version/implementation of symbol strip tool.

    @TaskAction
    protected void stripSymbols() {
        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        StripperSpec spec = new DefaultStripperSpec();
        spec.setBinaryFile(getBinaryFile().get().getAsFile());
        spec.setOutputFile(getOutputFile().get().getAsFile());
        spec.setOperationLogger(operationLogger);

        Compiler<StripperSpec> symbolStripper = createCompiler();
        symbolStripper = BuildOperationLoggingCompilerDecorator.wrap(symbolStripper);
        WorkResult result = symbolStripper.execute(spec);
        setDidWork(result.getDidWork());
    }

    private Compiler<StripperSpec> createCompiler() {
        NativePlatformInternal targetPlatform = Cast.cast(NativePlatformInternal.class, this.getTargetPlatform().get());
        NativeToolChainInternal toolChain = Cast.cast(NativeToolChainInternal.class, getToolChain().get());
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        return toolProvider.newCompiler(StripperSpec.class);
    }
}
