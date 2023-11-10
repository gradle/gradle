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
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultAfterExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Formatter;
import java.util.List;

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE;

public class SkipUpToDateStep<C extends IncrementalChangesContext> implements Step<C, UpToDateResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipUpToDateStep.class);

    private final Step<? super C, ? extends AfterExecutionResult> delegate;

    public SkipUpToDateStep(Step<? super C, ? extends AfterExecutionResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    public UpToDateResult execute(UnitOfWork work, C context) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Determining if {} is up-to-date", work.getDisplayName());
        }
        ImmutableList<String> reasons = context.getRebuildReasons();
        return context.getChanges()
            .filter(__ -> reasons.isEmpty())
            .map(changes -> skipExecution(work, changes.getBeforeExecutionState(), context))
            .orElseGet(() -> executeBecause(work, reasons, context));
    }

    private UpToDateResult skipExecution(UnitOfWork work, BeforeExecutionState beforeExecutionState, C context) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Skipping {} as it is up-to-date.", work.getDisplayName());
        }
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        PreviousExecutionState previousExecutionState = context.getPreviousExecutionState().get();
        AfterExecutionState afterExecutionState = new DefaultAfterExecutionState(
            beforeExecutionState,
            previousExecutionState.getOutputFilesProducedByWork(),
            true,
            previousExecutionState.getOriginMetadata(),
            true);
        Try<Execution> execution = Try.successful(Execution.skipped(UP_TO_DATE, work));
        return new UpToDateResult(
            previousExecutionState.getOriginMetadata().getExecutionTime(),
            execution,
            afterExecutionState,
            ImmutableList.of(),
            previousExecutionState.getOriginMetadata()
        );
    }

    private UpToDateResult executeBecause(UnitOfWork work, ImmutableList<String> reasons, C context) {
        logExecutionReasons(reasons, work);
        AfterExecutionResult result = delegate.execute(work, context);
        return new UpToDateResult(result, reasons, result.getAfterExecutionState()
            .filter(AfterExecutionState::isReused)
            .map(AfterExecutionState::getOriginMetadata)
            .orElse(null));
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
