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
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.caching.CachingStateFactory;
import org.gradle.internal.execution.caching.impl.DefaultCachingStateFactory;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.Formatter;
import java.util.List;

public class ResolveCachingStateStep<C extends ValidationFinishedContext> implements Step<C, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveCachingStateStep.class);
    private static final CachingDisabledReason BUILD_CACHE_DISABLED_REASON = new CachingDisabledReason(CachingDisabledReasonCategory.BUILD_CACHE_DISABLED, "Build cache is disabled");
    private static final CachingState BUILD_CACHE_DISABLED_STATE = CachingState.disabledWithoutInputs(BUILD_CACHE_DISABLED_REASON);
    private static final CachingDisabledReason VALIDATION_FAILED_REASON = new CachingDisabledReason(CachingDisabledReasonCategory.VALIDATION_FAILURE, "Caching has been disabled to ensure correctness. Please consult deprecation warnings for more details.");
    private static final CachingState VALIDATION_FAILED_STATE = CachingState.disabledWithoutInputs(VALIDATION_FAILED_REASON);

    private final BuildCacheController buildCache;
    private final boolean buildScansEnabled;
    private final Step<? super CachingContext, ? extends UpToDateResult> delegate;

    public ResolveCachingStateStep(
        BuildCacheController buildCache,
        boolean buildScansEnabled,
        Step<? super CachingContext, ? extends UpToDateResult> delegate
    ) {
        this.buildCache = buildCache;
        this.buildScansEnabled = buildScansEnabled;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, C context) {
        CachingState cachingState;
        if (!buildCache.isEnabled() && !buildScansEnabled) {
            cachingState = BUILD_CACHE_DISABLED_STATE;
        } else if (!context.getValidationProblems().isEmpty()) {
            cachingState = VALIDATION_FAILED_STATE;
        } else {
            cachingState = context.getBeforeExecutionState()
                .map(beforeExecutionState -> calculateCachingState(work, beforeExecutionState))
                .orElseGet(() -> calculateCachingStateWithNoCapturedInputs(work));
        }

        cachingState.apply(
            enabled -> logCacheKey(enabled.getKey(), work),
            disabled -> logDisabledReasons(disabled.getDisabledReasons(), work)
        );

        UpToDateResult result = delegate.execute(work, new CachingContext(context, cachingState));
        return new CachingResult(result, cachingState);
    }

    private CachingState calculateCachingState(UnitOfWork work, BeforeExecutionState beforeExecutionState) {
        Logger logger = buildCache.isEmitDebugLogging()
            ? LOGGER
            : NOPLogger.NOP_LOGGER;
        CachingStateFactory cachingStateFactory = new DefaultCachingStateFactory(logger);

        ImmutableList.Builder<CachingDisabledReason> cachingDisabledReasonsBuilder = ImmutableList.builder();
        if (!buildCache.isEnabled()) {
            cachingDisabledReasonsBuilder.add(BUILD_CACHE_DISABLED_REASON);
        }
        OverlappingOutputs detectedOverlappingOutputs = beforeExecutionState.getDetectedOverlappingOutputs()
            .orElse(null);
        work.shouldDisableCaching(detectedOverlappingOutputs)
            .ifPresent(cachingDisabledReasonsBuilder::add);

        return cachingStateFactory.createCachingState(beforeExecutionState, cachingDisabledReasonsBuilder.build());
    }

    private CachingState calculateCachingStateWithNoCapturedInputs(UnitOfWork work) {
        if (!buildCache.isEnabled()) {
            return BUILD_CACHE_DISABLED_STATE;
        }
        return work.shouldDisableCaching(null)
            .map(CachingState::disabledWithoutInputs)
            .orElse(CachingState.NOT_DETERMINED);
    }

    private void logCacheKey(BuildCacheKey cacheKey, UnitOfWork work) {
        if (buildCache.isEmitDebugLogging()) {
            LOGGER.warn("Build cache key for {} is {}", work.getDisplayName(), cacheKey.getDisplayName());
        } else {
            LOGGER.info("Build cache key for {} is {}", work.getDisplayName(), cacheKey.getDisplayName());
        }
    }

    private void logDisabledReasons(List<CachingDisabledReason> reasons, UnitOfWork work) {
        if (LOGGER.isInfoEnabled()) {
            Formatter formatter = new Formatter();
            formatter.format("Caching disabled for %s because:", work.getDisplayName());
            for (CachingDisabledReason reason : reasons) {
                formatter.format("%n  %s", reason.getMessage());
            }
            LOGGER.info(formatter.toString());
        }
    }
}
