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
    public CachingResult execute(UnitOfWork work) {
        return executeStep.execute(work, new Request(null));
    }

    @Override
    public CachingResult rebuild(UnitOfWork work, String reason) {
        return executeStep.execute(work, new Request(reason));
    }

    @Override
    public <T, O> T getFromIdentityCacheOrDeferExecution(UnitOfWork work, Cache<Identity, Try<O>> cache, DeferredExecutionHandler<O, T> handler) {
        return executeStep.executeDeferred(work, new Request(null), cache, handler);
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
