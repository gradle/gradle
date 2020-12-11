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
    public Builder createRequest(UnitOfWork work) {
        return new BuilderImpl(work);
    }

    private class BuilderImpl implements Builder {
        private final UnitOfWork work;
        private String rebuildReason;

        public BuilderImpl(UnitOfWork work) {
            this.work = work;
        }

        @Override
        public Builder forceRebuild(String rebuildReason) {
            this.rebuildReason = rebuildReason;
            return this;
        }

        @Override
        public CachingResult execute() {
            return executeStep.execute(work, new Request(rebuildReason));
        }

        @Override
        public <T, O> T getFromIdentityCacheOrDeferExecution(Cache<Identity, Try<O>> cache, DeferredExecutionHandler<O, T> handler) {
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
