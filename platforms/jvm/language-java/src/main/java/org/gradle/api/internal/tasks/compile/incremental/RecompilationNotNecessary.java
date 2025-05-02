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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.internal.tasks.compile.incremental.recomp.IncrementalCompilationResult;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationData;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.WorkResult;

public class RecompilationNotNecessary implements WorkResult, IncrementalCompilationResult {

    private final PreviousCompilationData previousCompilationData;
    private final RecompilationSpec recompilationSpec;

    public RecompilationNotNecessary(PreviousCompilationData previousCompilationData, RecompilationSpec recompilationSpec) {
        this.previousCompilationData = previousCompilationData;
        this.recompilationSpec = recompilationSpec;
    }

    @Override
    public boolean getDidWork() {
        return false;
    }

    @Override
    public WorkResult getCompilerResult() {
        return this;
    }

    @Override
    public PreviousCompilationData getPreviousCompilationData() {
        return previousCompilationData;
    }

    @Override
    public RecompilationSpec getRecompilationSpec() {
        return recompilationSpec;
    }
}
