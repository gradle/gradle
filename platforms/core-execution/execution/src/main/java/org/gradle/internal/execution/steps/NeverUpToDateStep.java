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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.execution.UnitOfWork;

public class NeverUpToDateStep<C extends CachingContext> implements Step<C, UpToDateResult> {
    // TODO Use a better explanation?
    private static final ImmutableList<String> NO_HISTORY = ImmutableList.of("No history is available.");

    private final Step<? super IncrementalChangesContext, ? extends AfterExecutionResult> delegate;

    public NeverUpToDateStep(Step<? super IncrementalChangesContext, ? extends AfterExecutionResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    public UpToDateResult execute(UnitOfWork work, C context) {
        AfterExecutionResult result = delegate.execute(work, new IncrementalChangesContext(context, NO_HISTORY, null));
        return new UpToDateResult(result, NO_HISTORY);
    }
}
