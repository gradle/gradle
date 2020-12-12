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

import org.gradle.cache.Cache;
import org.gradle.internal.Try;
import org.gradle.internal.execution.DeferredExecutionHandler;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.steps.DeferredExecutionAwareStep;
import org.gradle.internal.execution.steps.ExecutionRequestContext;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultExecutionEngine implements ExecutionEngine {
    private final DeferredExecutionAwareStep<? super ExecutionRequestContext, ? extends Result> executeStep;

    public DefaultExecutionEngine(DeferredExecutionAwareStep<? super ExecutionRequestContext, ? extends Result> executeStep) {
        this.executeStep = executeStep;
    }

    @Override
    public DirectExecutionRequestBuilder createRequest(UnitOfWork work) {
        return new DirectBuilder(work);
    }

    private static abstract class AbstractBuilder implements Builder {
        protected final UnitOfWork work;
        private String rebuildReason;
        private WorkValidationContext validationContext;

        public AbstractBuilder(UnitOfWork work) {
            this(work, null, null);
        }

        public AbstractBuilder(AbstractBuilder original) {
            this(original.work, original.rebuildReason, original.validationContext);
        }

        private AbstractBuilder(UnitOfWork work, @Nullable String rebuildReason, @Nullable WorkValidationContext validationContext) {
            this.work = work;
            this.rebuildReason = rebuildReason;
            this.validationContext = validationContext;
        }

        @Override
        public Builder forceRebuild(String rebuildReason) {
            this.rebuildReason = rebuildReason;
            return this;
        }

        @Override
        public Builder withValidationContext(WorkValidationContext validationContext) {
            this.validationContext = validationContext;
            return this;
        }

        protected ExecutionRequestContext createExecutionRequestContext() {
            WorkValidationContext validationContext = this.validationContext != null
                ? this.validationContext
                : new DefaultWorkValidationContext();
            return new ExecutionRequestContext() {
                @Override
                public Optional<String> getRebuildReason() {
                    return Optional.ofNullable(rebuildReason);
                }

                @Override
                public WorkValidationContext getValidationContext() {
                    return validationContext;
                }
            };
        }
    }

    private class DirectBuilder extends AbstractBuilder implements DirectExecutionRequestBuilder {
        public DirectBuilder(UnitOfWork work) {
            super(work);
        }

        @Override
        public DirectExecutionRequestBuilder forceRebuild(String rebuildReason) {
            super.forceRebuild(rebuildReason);
            return this;
        }

        @Override
        public DirectExecutionRequestBuilder withValidationContext(WorkValidationContext validationContext) {
            super.withValidationContext(validationContext);
            return this;
        }

        @Override
        public Result execute() {
            return executeStep.execute(work, createExecutionRequestContext());
        }

        @Override
        public <O> DeferredExecutionRequestBuilder<O> withIdentityCache(Cache<Identity, Try<O>> cache) {
            return new CachedBuilder<>(cache, this);
        }
    }

    private class CachedBuilder<O> extends AbstractBuilder implements DeferredExecutionRequestBuilder<O> {
        private final Cache<Identity, Try<O>> cache;

        public CachedBuilder(Cache<Identity, Try<O>> cache, AbstractBuilder original) {
            super(original);
            this.cache = cache;
        }

        @Override
        public DeferredExecutionRequestBuilder<O> forceRebuild(String rebuildReason) {
            super.forceRebuild(rebuildReason);
            return this;
        }

        @Override
        public DeferredExecutionRequestBuilder<O> withValidationContext(WorkValidationContext validationContext) {
            super.withValidationContext(validationContext);
            return this;
        }

        @Override
        public <T> T getOrDeferExecution(DeferredExecutionHandler<O, T> handler) {
            return executeStep.executeDeferred(work, createExecutionRequestContext(), cache, handler);
        }
    }
}
