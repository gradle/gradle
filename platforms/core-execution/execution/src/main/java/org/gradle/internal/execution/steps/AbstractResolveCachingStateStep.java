/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.internal.hash.HashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.Formatter;
import java.util.List;
import java.util.Optional;

public abstract class AbstractResolveCachingStateStep<C extends ValidationFinishedContext> implements Step<C, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResolveCachingStateStep.class);
    private static final CachingDisabledReason BUILD_CACHE_DISABLED_REASON = new CachingDisabledReason(CachingDisabledReasonCategory.BUILD_CACHE_DISABLED, "Build cache is disabled");
    private static final CachingState BUILD_CACHE_DISABLED_STATE = CachingState.disabledWithoutInputs(BUILD_CACHE_DISABLED_REASON);
    private static final CachingDisabledReason VALIDATION_FAILED_REASON = new CachingDisabledReason(CachingDisabledReasonCategory.VALIDATION_FAILURE, "Caching has been disabled to ensure correctness. Please consult deprecation warnings for more details.");
    private static final CachingState VALIDATION_FAILED_STATE = CachingState.disabledWithoutInputs(VALIDATION_FAILED_REASON);

    private final BuildCacheController buildCache;
    private final boolean emitDebugLogging;

    public AbstractResolveCachingStateStep(
        BuildCacheController buildCache,
        boolean emitDebugLogging
    ) {
        this.buildCache = buildCache;
        this.emitDebugLogging = emitDebugLogging;
    }

    @Override
    public CachingResult execute(UnitOfWork work, C context) {
        CachingState cachingState;
        cachingState = context.getBeforeExecutionState()
            .map(beforeExecutionState -> calculateCachingState(work, context, beforeExecutionState))
            .orElseGet(() -> !context.getValidationProblems().isEmpty()
                ? VALIDATION_FAILED_STATE
                : calculateCachingStateWithNoCapturedInputs(work));

        cachingState.apply(
            enabled -> logCacheKey(enabled.getCacheKeyCalculatedState().getKey(), work),
            disabled -> logDisabledReasons(disabled.getDisabledReasons(), work)
        );

        UpToDateResult result = executeDelegate(work, context, cachingState);
        return new CachingResult(result, cachingState);
    }

    private CachingState calculateCachingState(UnitOfWork work, C context, BeforeExecutionState beforeExecutionState) {
        Logger logger = emitDebugLogging
            ? LOGGER
            : NOPLogger.NOP_LOGGER;
        CachingStateFactory cachingStateFactory = new DefaultCachingStateFactory(logger);
        HashCode cacheKey = getPreviousCacheKeyIfApplicable(context)
            .orElseGet(() -> cachingStateFactory.calculateCacheKey(beforeExecutionState));
        ImmutableList.Builder<CachingDisabledReason> cachingDisabledReasonsBuilder = ImmutableList.builder();
        if (!context.getValidationProblems().isEmpty()) {
            cachingDisabledReasonsBuilder.add(VALIDATION_FAILED_REASON);
        }
        if (!buildCache.isEnabled()) {
            cachingDisabledReasonsBuilder.add(BUILD_CACHE_DISABLED_REASON);
        }
        OverlappingOutputs detectedOverlappingOutputs = beforeExecutionState.getDetectedOverlappingOutputs()
            .orElse(null);
        work.shouldDisableCaching(detectedOverlappingOutputs)
            .ifPresent(cachingDisabledReasonsBuilder::add);

        return cachingStateFactory.createCachingState(beforeExecutionState, cacheKey, cachingDisabledReasonsBuilder.build());
    }

    /**
     * Return cache key from previous build if there are no changes.
     */
    protected abstract Optional<HashCode> getPreviousCacheKeyIfApplicable(C context);

    protected abstract UpToDateResult executeDelegate(UnitOfWork work, C context, CachingState cachingState);

    private CachingState calculateCachingStateWithNoCapturedInputs(UnitOfWork work) {
        if (!buildCache.isEnabled()) {
            return BUILD_CACHE_DISABLED_STATE;
        }
        return work.shouldDisableCaching(null)
            .map(CachingState::disabledWithoutInputs)
            .orElse(CachingState.NOT_DETERMINED);
    }

    private void logCacheKey(BuildCacheKey cacheKey, UnitOfWork work) {
        if (emitDebugLogging) {
            LOGGER.warn("Build cache key for {} is {}", work.getDisplayName(), cacheKey.getHashCode());
        } else {
            LOGGER.info("Build cache key for {} is {}", work.getDisplayName(), cacheKey.getHashCode());
        }
    }

    private static void logDisabledReasons(List<CachingDisabledReason> reasons, UnitOfWork work) {
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
