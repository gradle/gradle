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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.cache.CacheRepository;
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner;
import org.gradle.language.jvm.internal.StaleClassCleaner;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

import java.io.File;

public class CleanCompilingNativeCompiler extends AbstractIncrementalNativeCompiler {
    private final Compiler<NativeCompileSpec> delegateCompiler;

    public CleanCompilingNativeCompiler(TaskInternal task, Iterable<File> includes, CacheRepository cacheRepository, Compiler<NativeCompileSpec> delegateCompiler) {
        super(task, includes, cacheRepository);
        this.delegateCompiler = delegateCompiler;
    }

    @Override
    protected WorkResult doIncrementalCompile(IncrementalCompileProcessor processor, NativeCompileSpec spec) {
        processor.processSourceFiles(spec.getSourceFiles());
        cleanPreviousOutputs(spec);
        return delegateCompiler.execute(spec);
    }

    private void cleanPreviousOutputs(NativeCompileSpec spec) {
        StaleClassCleaner cleaner = new SimpleStaleClassCleaner(getTask().getOutputs());
        cleaner.setDestinationDir(spec.getObjectFileDir());
        cleaner.execute();
    }

}
