/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.c.internal.incremental;

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner;
import org.gradle.language.jvm.internal.StaleClassCleaner;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

public class CleanIncrementalCompiler implements Compiler<NativeCompileSpec> {
    private final Compiler<NativeCompileSpec> delegateCompiler;
    private final IncrementalCompileProcessor incrementalCompileProcessor;
    private TaskOutputsInternal taskOutputs;

    public CleanIncrementalCompiler(Compiler<NativeCompileSpec> delegateCompiler, IncrementalCompileProcessor incrementalCompileProcessor,
                                    TaskOutputsInternal taskOutputs) {
        this.delegateCompiler = delegateCompiler;
        this.incrementalCompileProcessor = incrementalCompileProcessor;
        this.taskOutputs = taskOutputs;
    }

    public WorkResult execute(NativeCompileSpec spec) {
        incrementalCompileProcessor.processSourceFiles(spec.getSourceFiles());
        cleanPreviousOutputs(spec);
        return delegateCompiler.execute(spec);
    }

    private void cleanPreviousOutputs(NativeCompileSpec spec) {
        StaleClassCleaner cleaner = new SimpleStaleClassCleaner(taskOutputs);
        cleaner.setDestinationDir(spec.getObjectFileDir());
        cleaner.execute();
    }

}
