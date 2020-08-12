/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.tasks.WorkResult;
import org.gradle.workers.internal.DefaultWorkResult;

/**
 * Marks compilation as beeing performed incrementally.
 */
public class DefaultIncrementalCompileResult extends DefaultWorkResult implements IncrementalCompilationResult {
    private final WorkResult compilerResult;

    public DefaultIncrementalCompileResult(WorkResult compilerResult) {
        super(compilerResult.getDidWork(), maybeException(compilerResult));
        this.compilerResult = compilerResult;
    }

    private static Throwable maybeException(WorkResult workResult) {
        if (workResult instanceof DefaultWorkResult) {
            return ((DefaultWorkResult) workResult).getException();
        }
        return null;
    }

    @Override
    public WorkResult getCompilerResult() {
        return compilerResult;
    }
}
