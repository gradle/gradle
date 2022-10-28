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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.cache.Cache;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.steps.DeferredExecutionAwareStep;
import org.gradle.internal.execution.steps.ExecutionRequestContext;

public class DefaultExecutionEngine implements ExecutionEngine {
    private final DocumentationRegistry documentationRegistry;
    private final DeferredExecutionAwareStep<? super ExecutionRequestContext, ? extends Result> executeStep;

    public DefaultExecutionEngine(DocumentationRegistry documentationRegistry, DeferredExecutionAwareStep<? super ExecutionRequestContext, ? extends Result> executeStep) {
        this.documentationRegistry = documentationRegistry;
        this.executeStep = executeStep;
    }

    @Override
    public Request createRequest(UnitOfWork work) {
        return new Request() {
            private String nonIncrementalReason;
            private WorkValidationContext validationContext;

            private ExecutionRequestContext createExecutionRequestContext() {
                WorkValidationContext validationContext = this.validationContext != null
                    ? this.validationContext
                    : new DefaultWorkValidationContext(documentationRegistry, work.getTypeOriginInspector());
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
                return executeStep.execute(work, createExecutionRequestContext());
            }

            @Override
            public <T> Deferrable<Try<T>> executeDeferred(Cache<Identity, Try<T>> cache) {
                return executeStep.executeDeferred(work, createExecutionRequestContext(), cache);
            }
        };
    }
}
