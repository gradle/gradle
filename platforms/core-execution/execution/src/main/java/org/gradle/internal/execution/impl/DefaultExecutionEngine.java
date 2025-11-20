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

package org.gradle.internal.execution.impl;

import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.cache.Cache;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.DeferredResult;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.steps.DeferredExecutionAwareStep;
import org.gradle.internal.execution.steps.ExecutionRequestContext;
import org.jspecify.annotations.Nullable;

public class DefaultExecutionEngine implements ExecutionEngine {
    private final DeferredExecutionAwareStep<? super ExecutionRequestContext, ? extends Result> pipeline;
    private final InternalProblems problems;

    public DefaultExecutionEngine(DeferredExecutionAwareStep<? super ExecutionRequestContext, ? extends Result> pipeline, InternalProblems problems) {
        this.pipeline = pipeline;
        this.problems = problems;
    }

    @Override
    public Request createRequest(UnitOfWork work) {
        return new Request() {
            @Nullable
            private String nonIncrementalReason;
            @Nullable
            private WorkValidationContext validationContext;

            private ExecutionRequestContext createExecutionRequestContext() {
                WorkValidationContext validationContext = this.validationContext != null
                    ? this.validationContext
                    : new DefaultWorkValidationContext(WorkValidationContext.TypeOriginInspector.NO_OP, problems);
                return new ExecutionRequestContext(nonIncrementalReason, validationContext);
            }

            @Override
            public void forceNonIncremental(String nonIncremental) {
                this.nonIncrementalReason = nonIncremental;
            }

            @Override
            public void withValidationContext(WorkValidationContext validationContext) {
                this.validationContext = validationContext;
            }

            @Override
            public Result execute() {
                return pipeline.execute(work, createExecutionRequestContext());
            }

            @Override
            public <T> Deferrable<Try<T>> executeDeferred(Cache<Identity, DeferredResult<T>> cache) {
                return pipeline.executeDeferred(work, createExecutionRequestContext(), cache);
            }
        };
    }
}
