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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.time.Duration;
import java.util.Optional;

public class SkipEmptyWorkStep<C extends PreviousExecutionContext> implements Step<C, CachingResult> {
    private final Step<? super C, ? extends CachingResult> delegate;

    public SkipEmptyWorkStep(Step<? super C, ? extends CachingResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, C context) {
        ImmutableSortedMap<String, FileSystemSnapshot> previousOutputFiles = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());
        UnitOfWork.Identity identity = context.getIdentity();
        return work.skipIfInputsEmpty(previousOutputFiles)
            .map(skippedOutcome -> {
                context.getHistory()
                    .ifPresent(history -> history.remove(identity.getUniqueId()));
                return (CachingResult) new CachingResult() {
                    @Override
                    public Try<ExecutionResult> getExecutionResult() {
                        return Try.successful(new ExecutionResult() {
                            @Override
                            public ExecutionOutcome getOutcome() {
                                return skippedOutcome;
                            }

                            @Override
                            public Object getOutput() {
                                return work.loadRestoredOutput(context.getWorkspace());
                            }
                        });
                    }

                    @Override
                    public CachingState getCachingState() {
                        return CachingState.NOT_DETERMINED;
                    }

                    @Override
                    public ImmutableList<String> getExecutionReasons() {
                        return ImmutableList.of();
                    }

                    @Override
                    public Optional<AfterExecutionState> getAfterExecutionState() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                        return Optional.empty();
                    }

                    @Override
                    public Duration getDuration() {
                        return Duration.ZERO;
                    }
                };
            })
            .orElseGet(() -> delegate.execute(work, context));
    }
}
