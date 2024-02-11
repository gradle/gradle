/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class IncrementalSwiftCompiler implements Compiler<SwiftCompileSpec> {
    private final Compiler<SwiftCompileSpec> compiler;
    private final TaskOutputsInternal outputs;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final Deleter deleter;

    public IncrementalSwiftCompiler(
        Compiler<SwiftCompileSpec> compiler,
        TaskOutputsInternal outputs,
        CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory,
        Deleter deleter
    ) {
        this.compiler = compiler;
        this.outputs = outputs;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.deleter = deleter;
    }

    @Override
    public WorkResult execute(SwiftCompileSpec spec) {
        final boolean didRemove;
        if (spec.isIncrementalCompile()) {
            didRemove = deleteOutputsForRemovedSources(spec);
        } else {
            didRemove = cleanPreviousOutputs(spec);
        }

        WorkResult compileResult = compile(spec);
        return WorkResults.didWork(didRemove || compileResult.getDidWork());
    }

    protected WorkResult compile(SwiftCompileSpec spec) {
        return compiler.execute(spec);
    }

    private boolean deleteOutputsForRemovedSources(SwiftCompileSpec spec) {
        boolean didRemove = false;
        for (File removedSource : spec.getRemovedSourceFiles()) {
            File objectFile = getObjectFile(spec.getObjectFileDir(), removedSource);

            try {
                if (deleter.deleteRecursively(objectFile.getParentFile())) {
                    didRemove = true;
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return didRemove;
    }

    private File getObjectFile(File objectFileRoot, File sourceFile) {
        return compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(".o") // TODO: Get this from somewhere else?
            .withOutputBaseFolder(objectFileRoot)
            .map(sourceFile);
    }

    private boolean cleanPreviousOutputs(SwiftCompileSpec spec) {
        return StaleOutputCleaner.cleanOutputs(deleter, outputs.getPreviousOutputFiles(), spec.getObjectFileDir());
    }
}
