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

import com.google.common.cache.Cache;
import org.gradle.internal.Try;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.DeferredExecutionAwareStep;
import org.gradle.internal.execution.DeferredResultProcessor;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultExecutionEngine implements ExecutionEngine {
    private final DeferredExecutionAwareStep<? super ExecutionRequestContext, CachingResult> executeStep;

    public DefaultExecutionEngine(DeferredExecutionAwareStep<? super ExecutionRequestContext, CachingResult> executeStep) {
        this.executeStep = executeStep;
    }

    @Override
    public CachingResult execute(UnitOfWork work, @Nullable String rebuildReason) {
        return executeStep.execute(work, new Request(rebuildReason));
    }

    @Override
    public <T, O> T executeDeferred(UnitOfWork work, @Nullable String rebuildReason, Cache<Identity, Try<O>> cache, DeferredResultProcessor<O, T> processor) {
        return executeStep.executeDeferred(work, new Request(rebuildReason), cache, processor);
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
