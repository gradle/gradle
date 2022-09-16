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

package org.gradle.internal.execution.steps.legacy;

import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.steps.CachingContext;
import org.gradle.internal.execution.steps.UpToDateResult;

/**
 * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
 */
public class MarkSnapshottingInputsFinishedStep<C extends CachingContext> implements UpToDateResult.Step<C> {
    private final UpToDateResult.Step<? super C> delegate;

    public MarkSnapshottingInputsFinishedStep(UpToDateResult.Step<? super C> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> UpToDateResult<T> execute(UnitOfWork<T> work, C context) {
        work.markLegacySnapshottingInputsFinished(context.getCachingState());
        return delegate.execute(work, context);
    }
}
