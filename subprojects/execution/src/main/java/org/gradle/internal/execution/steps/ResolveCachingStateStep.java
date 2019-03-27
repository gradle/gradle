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
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalContext;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UpToDateResult;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCatwgory;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.caching.CachingStateBuilder;
import org.gradle.internal.execution.caching.impl.DefaultCachingStateBuilder;
import org.gradle.internal.execution.caching.impl.LoggingCachingStateBuilder;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Formatter;
import java.util.List;
import java.util.Optional;

public class ResolveCachingStateStep implements Step<IncrementalContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveCachingStateStep.class);
    private static final CachingDisabledReason BUILD_CACHE_DISABLED = new CachingDisabledReason(CachingDisabledReasonCatwgory.BUILD_CACHE_DISABLED, "Build cache is disabled");

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
    public CachingResult execute(IncrementalContext context) {
        UnitOfWork work = context.getWork();
        CachingState cachingState;
        if (!buildCache.isEnabled() && !buildScansEnabled) {
            cachingState = CachingState.NOT_DETERMINED;
        } else {
            cachingState = context.getBeforeExecutionState()
                .map(beforeExecutionState -> calculateCachingState(beforeExecutionState, work))
                .orElse(CachingState.NOT_DETERMINED);
        }

        cachingState.getKey().apply(
            noCachingReasons -> {
                logNoCachingReasons(noCachingReasons, work);
            },
            cacheKey -> {
                logCacheKey(cacheKey, work);
            });

        UpToDateResult result = delegate.execute(new CachingContext() {
            @Override
            public CachingState getCachingState() {
                return cachingState;
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }

            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return context.getAfterPreviousExecutionState();
            }

            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return context.getBeforeExecutionState();
            }

            @Override
            public UnitOfWork getWork() {
                return work;
            }
        });
        return new CachingResult() {
            @Override
            public CachingState getCachingState() {
                return cachingState;
            }

            @Override
            public ImmutableList<String> getExecutionReasons() {
                return result.getExecutionReasons();
            }

            @Override
            public ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getFinalOutputs() {
                return result.getFinalOutputs();
            }

            @Override
            public OriginMetadata getOriginMetadata() {
                return result.getOriginMetadata();
            }

            @Override
            public boolean isReused() {
                return result.isReused();
            }

            @Override
            public Try<ExecutionOutcome> getOutcome() {
                return result.getOutcome();
            }
        };
    }

    private CachingState calculateCachingState(BeforeExecutionState executionState, UnitOfWork work) {
        CachingStateBuilder builder = buildCache.isEmitDebugLogging()
            ? new LoggingCachingStateBuilder()
            : new DefaultCachingStateBuilder();

        if (!buildCache.isEnabled()) {
            builder.markNotCacheable(BUILD_CACHE_DISABLED);
        }
        work.shouldDisableCaching().ifPresent(noCacheReason -> {
            builder.markNotCacheable(noCacheReason);
        });

        builder.appendImplementation(executionState.getImplementation());
        executionState.getAdditionalImplementations().forEach(additionalImplementation -> {
            builder.appendAdditionalImplementation(additionalImplementation);
        });
        executionState.getInputProperties().forEach((propertyName, fingerprint) -> {
            builder.appendInputValueFingerprint(propertyName, fingerprint);
        });
        executionState.getInputFileProperties().forEach((propertyName, fingerprint) -> {
            builder.appendInputFilesPropertyFingerprints(propertyName, fingerprint);
        });
        executionState.getOutputFileProperties().keySet().forEach(propertyName -> {
            builder.appendOutputPropertyName(propertyName);
        });

        return builder.build();
    }

    private void logCacheKey(BuildCacheKey cacheKey, UnitOfWork work) {
        if (buildCache.isEmitDebugLogging()) {
            LOGGER.warn("Build cache key for {} is {}", work.getDisplayName(), cacheKey.getDisplayName());
        } else {
            LOGGER.info("Build cache key for {} is {}", work.getDisplayName(), cacheKey.getDisplayName());
        }
    }

    private void logNoCachingReasons(List<CachingDisabledReason> reasons, UnitOfWork work) {
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
