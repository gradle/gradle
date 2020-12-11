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
import org.gradle.internal.execution.steps.CachingResult;
import org.gradle.internal.execution.steps.DeferredExecutionAwareStep;
import org.gradle.internal.execution.steps.ExecutionRequestContext;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultExecutionEngine implements ExecutionEngine {
    private final DeferredExecutionAwareStep<? super ExecutionRequestContext, CachingResult> executeStep;

    public DefaultExecutionEngine(DeferredExecutionAwareStep<? super ExecutionRequestContext, CachingResult> executeStep) {
        this.executeStep = executeStep;
    }

    @Override
    public DirectExecutionRequestBuilder createRequest(UnitOfWork work) {
        return new DirectBuilder(work);
    }

    private static abstract class AbstractBuilder implements Builder {
        protected final UnitOfWork work;
        protected String rebuildReason;

        public AbstractBuilder(UnitOfWork work) {
            this(work, null);
        }

        public AbstractBuilder(AbstractBuilder original) {
            this(original.work, original.rebuildReason);
        }

        private AbstractBuilder(UnitOfWork work, @Nullable String rebuildReason) {
            this.work = work;
            this.rebuildReason = rebuildReason;
        }

        @Override
        public Builder forceRebuild(String rebuildReason) {
            this.rebuildReason = rebuildReason;
            return this;
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
        public Result execute() {
            return executeStep.execute(work, new Request(rebuildReason));
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
        public <T> T getOrDeferExecution(DeferredExecutionHandler<O, T> handler) {
            return executeStep.executeDeferred(work, new Request(rebuildReason), cache, handler);
        }
    }

    private static class Request implements ExecutionRequestContext {
        private final String rebuildReason;

        public Request(@Nullable String rebuildReason) {
            this.rebuildReason = rebuildReason;
        }

        @Override
        public Optional<String> getRebuildReason() {
            return Optional.ofNullable(rebuildReason);
        }
    }
}
