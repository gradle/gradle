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
import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.lang.StringUtils;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.CurrentSnapshotResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalChangesContext;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UpToDateResult;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Formatter;
import java.util.List;
import java.util.Optional;

public class SkipUpToDateStep<C extends IncrementalChangesContext> implements Step<C, UpToDateResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipUpToDateStep.class);

    private static final ImmutableList<String> CHANGE_TRACKING_DISABLED = ImmutableList.of("Change tracking is disabled.");

    private final Step<? super C, ? extends CurrentSnapshotResult> delegate;

    public SkipUpToDateStep(Step<? super C, ? extends CurrentSnapshotResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    public UpToDateResult execute(C context) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Determining if {} is up-to-date", context.getWork().getDisplayName());
        }
        return context.getChanges().map(changes -> {
            ImmutableList<String> reasons = changes.getAllChangeMessages();
            if (reasons.isEmpty()) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Skipping {} as it is up-to-date.", context.getWork().getDisplayName());
                }
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                AfterPreviousExecutionState afterPreviousExecutionState = context.getAfterPreviousExecutionState().get();
                return new UpToDateResult() {
                    @Override
                    public ImmutableList<String> getExecutionReasons() {
                        return ImmutableList.of();
                    }

                    @Override
                    public ImmutableSortedMap<String, FileCollectionFingerprint> getFinalOutputs() {
                        return afterPreviousExecutionState.getOutputFileProperties();
                    }

                    @Override
                    public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                        return Optional.of(afterPreviousExecutionState.getOriginMetadata());
                    }

                    @Override
                    public Try<ExecutionOutcome> getOutcome() {
                        return Try.successful(ExecutionOutcome.UP_TO_DATE);
                    }
                };
            } else {
                return executeBecause(reasons, context);
            }
        }).orElseGet(() -> executeBecause(CHANGE_TRACKING_DISABLED, context));
    }

    private UpToDateResult executeBecause(ImmutableList<String> reasons, C context) {
        logExecutionReasons(reasons, context.getWork());
        CurrentSnapshotResult result = delegate.execute(context);
        return new UpToDateResult() {
            @Override
            public ImmutableList<String> getExecutionReasons() {
                return reasons;
            }

            @Override
            public ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getFinalOutputs() {
                return result.getFinalOutputs();
            }

            @Override
            public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                return result.isReused()
                    ? Optional.of(result.getOriginMetadata())
                    : Optional.empty();
            }

            @Override
            public Try<ExecutionOutcome> getOutcome() {
                return result.getOutcome();
            }
        };
    }

    private void logExecutionReasons(List<String> reasons, UnitOfWork work) {
        if (LOGGER.isInfoEnabled()) {
            Formatter formatter = new Formatter();
            formatter.format("%s is not up-to-date because:", StringUtils.capitalize(work.getDisplayName()));
            for (String message : reasons) {
                formatter.format("%n  %s", message);
            }
            LOGGER.info(formatter.toString());
        }
    }
}
