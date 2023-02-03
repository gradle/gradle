/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;

import java.io.File;

public class OutputCleaningCompiler<T extends NativeCompileSpec> implements Compiler<T> {

    private final Compiler<T> compiler;
    private final String outputFileSuffix;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;

    public OutputCleaningCompiler(Compiler<T> compiler, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, String outputFileSuffix) {
        this.compiler = compiler;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.outputFileSuffix = outputFileSuffix;
    }

    @Override
    public WorkResult execute(T spec) {
        boolean didRemove = deleteOutputsForRemovedSources(spec);
        boolean didCompile = compileSources(spec);
        return WorkResults.didWork(didRemove || didCompile);
    }

    private boolean compileSources(T spec) {
        if (spec.getSourceFiles().isEmpty()) {
            return false;
        }
        return compiler.execute(spec).getDidWork();
    }

    private boolean deleteOutputsForRemovedSources(NativeCompileSpec spec) {
        boolean didRemove = false;
        for (File removedSource : spec.getRemovedSourceFiles()) {
            File objectFile = getObjectFile(spec.getObjectFileDir(), removedSource);

            // Remove .pdb file if present
            new File(objectFile.getParentFile(), objectFile.getName() + ".pdb").delete();

            if (objectFile.delete()) {
                didRemove = true;
                objectFile.getParentFile().delete();
            }
        }
        return didRemove;
    }

    private File getObjectFile(File objectFileRoot, File sourceFile) {
        return compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(outputFileSuffix)
            .withOutputBaseFolder(objectFileRoot)
            .map(sourceFile);
    }
}
