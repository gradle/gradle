/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal;

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.play.internal.spec.PlayCompileSpec;

public class CleaningPlayToolCompiler<T extends PlayCompileSpec> implements Compiler<T> {
    private final Compiler<T> delegate;
    private TaskOutputsInternal taskOutputs;
    private final Deleter deleter;

    public CleaningPlayToolCompiler(Compiler<T> delegate, TaskOutputsInternal taskOutputs, Deleter deleter) {
        this.delegate = delegate;
        this.taskOutputs = taskOutputs;
        this.deleter = deleter;
    }

    @Override
    public WorkResult execute(T spec) {
        boolean cleanedOutputs = StaleOutputCleaner.cleanOutputs(deleter, taskOutputs.getPreviousOutputFiles(), spec.getDestinationDir());
        return delegate.execute(spec)
            .or(WorkResults.didWork(cleanedOutputs));
    }
}
