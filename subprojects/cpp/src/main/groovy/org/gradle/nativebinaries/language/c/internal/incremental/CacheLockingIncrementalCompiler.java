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

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

import java.util.concurrent.atomic.AtomicReference;

public class CacheLockingIncrementalCompiler implements Compiler<NativeCompileSpec> {
    private final IncrementalCompileProcessor incrementalCompileProcessor;
    private final Compiler<NativeCompileSpec> delegateCompiler;

    public CacheLockingIncrementalCompiler(IncrementalCompileProcessor incrementalCompileProcessor, Compiler<NativeCompileSpec> delegateCompiler) {
        this.incrementalCompileProcessor = incrementalCompileProcessor;
        this.delegateCompiler = delegateCompiler;
    }

    public WorkResult execute(final NativeCompileSpec spec) {
        final AtomicReference<WorkResult> result = new AtomicReference<WorkResult>();
        incrementalCompileProcessor.getCacheAccess().useCache("incremental compile", new Runnable() {
            public void run() {
                result.set(delegateCompiler.execute(spec));
            }
        });
        return result.get();
    }
}
