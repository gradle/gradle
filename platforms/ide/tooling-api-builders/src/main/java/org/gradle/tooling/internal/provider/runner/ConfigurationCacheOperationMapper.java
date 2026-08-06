/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheDescriptor;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheEntryDiscardedResult;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheEntryNotStoredResult;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheEntryReusedResult;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheEntryStoredResult;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheEntryUndeterminedResult;
import org.gradle.internal.build.event.types.DefaultConfigurationCacheEntryUpdatedResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFailureResult;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.operations.configuration.ConfigurationCacheEntryOutcomeBuildOperationType;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static java.util.Collections.singletonList;

@NullMarked
public class ConfigurationCacheOperationMapper implements BuildOperationMapper<ConfigurationCacheEntryOutcomeBuildOperationType.Details, DefaultConfigurationCacheDescriptor> {
    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.CONFIGURATION_CACHE);
    }

    @Override
    public Class<ConfigurationCacheEntryOutcomeBuildOperationType.Details> getDetailsType() {
        return ConfigurationCacheEntryOutcomeBuildOperationType.Details.class;
    }

    @Override
    public DefaultConfigurationCacheDescriptor createDescriptor(ConfigurationCacheEntryOutcomeBuildOperationType.Details details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        return new DefaultConfigurationCacheDescriptor(buildOperation.getId(), buildOperation.getName(), buildOperation.getDisplayName(), parent);
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultConfigurationCacheDescriptor descriptor, ConfigurationCacheEntryOutcomeBuildOperationType.Details details, OperationStartEvent startEvent) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultConfigurationCacheDescriptor descriptor, ConfigurationCacheEntryOutcomeBuildOperationType.Details details, OperationFinishEvent finishEvent) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, toOperationResult(finishEvent));
    }

    private static AbstractOperationResult toOperationResult(OperationFinishEvent finishEvent) {
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        Throwable failure = finishEvent.getFailure();
        if (failure != null) {
            return new DefaultFailureResult(startTime, endTime, singletonList(DefaultFailure.fromThrowable(failure)));
        }
        ConfigurationCacheEntryOutcomeBuildOperationType.Result operationResult = (ConfigurationCacheEntryOutcomeBuildOperationType.Result) finishEvent.getResult();
        int problemCount = operationResult.getProblemCount();
        switch (operationResult.getOutcome()) {
            case STORED:
                return new DefaultConfigurationCacheEntryStoredResult(startTime, endTime, problemCount);
            case REUSED:
                return new DefaultConfigurationCacheEntryReusedResult(startTime, endTime, problemCount);
            case UPDATED:
                return new DefaultConfigurationCacheEntryUpdatedResult(startTime, endTime, problemCount);
            case DISCARDED:
                return new DefaultConfigurationCacheEntryDiscardedResult(startTime, endTime, problemCount);
            case NOT_STORED:
                return new DefaultConfigurationCacheEntryNotStoredResult(startTime, endTime, problemCount);
            case UNDETERMINED:
                return new DefaultConfigurationCacheEntryUndeterminedResult(startTime, endTime, problemCount);
            default:
                throw new IllegalStateException("Unknown configuration cache entry outcome: " + operationResult.getOutcome());
        }
    }
}
