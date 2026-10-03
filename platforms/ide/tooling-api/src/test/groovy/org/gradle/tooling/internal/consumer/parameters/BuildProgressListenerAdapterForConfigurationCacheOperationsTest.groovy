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

package org.gradle.tooling.internal.consumer.parameters

import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.SkippedResult
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryReusedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoreFailedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoreSkippedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoredResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryUndeterminedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheFinishEvent
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheDescriptor
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheEntryOutcomeResult
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheEntryReusedResult
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheEntryStoreFailedResult
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheEntryStoreSkippedResult
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheEntryStoredResult
import org.gradle.tooling.internal.protocol.events.InternalConfigurationCacheEntryUndeterminedResult
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationResult
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import spock.lang.Specification

class BuildProgressListenerAdapterForConfigurationCacheOperationsTest extends Specification {

    def "adapter is only subscribing to configuration cache events if at least one listener is attached"() {
        expect:
        createAdapter().subscribedOperations == []
        createAdapter(Mock(ProgressListener)).subscribedOperations == [InternalBuildProgressListener.CONFIGURATION_CACHE]
    }

    def "converts #internalType.simpleName to #expectedType.simpleName"() {
        given:
        def result = Stub(internalType) {
            getStartTime() >> 1
            getEndTime() >> 2
            getProblemCount() >> 3
            getFailures() >> []
        }

        when:
        def event = finishEventFor(result)

        then:
        expectedType.isInstance(event.result)
        genericType.isInstance(event.result)
        event.result.startTime == 1
        event.result.endTime == 2
        event.result.problemCount == 3

        where:
        internalType                                          | expectedType                              | genericType
        InternalConfigurationCacheEntryStoredResult           | ConfigurationCacheEntryStoredResult       | SuccessResult
        InternalConfigurationCacheEntryReusedResult           | ConfigurationCacheEntryReusedResult       | SuccessResult
        InternalConfigurationCacheEntryStoreSkippedResult     | ConfigurationCacheEntryStoreSkippedResult | SkippedResult
        InternalConfigurationCacheEntryStoreFailedResult      | ConfigurationCacheEntryStoreFailedResult  | FailureResult
        InternalConfigurationCacheEntryUndeterminedResult     | ConfigurationCacheEntryUndeterminedResult | SkippedResult
    }

    def "failed store carries its failures"() {
        given:
        def result = Stub(InternalConfigurationCacheEntryStoreFailedResult) {
            getFailures() >> [Stub(InternalFailure)]
        }

        when:
        def event = finishEventFor(result)

        then:
        (event.result as ConfigurationCacheEntryStoreFailedResult).failures.size() == 1
    }

    def "converts an outcome unknown to this client to an undetermined result"() {
        given:
        def result = Stub(InternalConfigurationCacheEntryOutcomeResult) {
            getStartTime() >> 1
            getEndTime() >> 2
            getProblemCount() >> 3
        }

        when:
        def event = finishEventFor(result)

        then:
        event.result instanceof ConfigurationCacheEntryUndeterminedResult
        event.result.startTime == 1
        event.result.endTime == 2
        event.result.problemCount == 3
    }

    def "converts a result without an outcome to an undetermined result"() {
        given:
        def result = Stub(InternalOperationResult) {
            getStartTime() >> 1
            getEndTime() >> 2
        }

        when:
        def event = finishEventFor(result)

        then:
        event.result instanceof ConfigurationCacheEntryUndeterminedResult
        event.result.problemCount == 0
    }

    private ConfigurationCacheFinishEvent finishEventFor(InternalOperationResult result) {
        def events = []
        def adapter = createAdapter({ events << it } as ProgressListener)

        def descriptor = Stub(InternalConfigurationCacheDescriptor) {
            getId() >> 1
            getName() >> 'Configuration cache entry outcome'
            getDisplayName() >> 'Configuration cache entry outcome'
            getParentId() >> null
        }
        // a finish event always assumes a previous start event
        adapter.onEvent(Stub(InternalOperationStartedProgressEvent) {
            getDescriptor() >> descriptor
        })
        adapter.onEvent(Stub(InternalOperationFinishedProgressEvent) {
            getDescriptor() >> descriptor
            getResult() >> result
        })

        events.last() as ConfigurationCacheFinishEvent
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener listener) {
        new BuildProgressListenerAdapter([(OperationType.CONFIGURATION_CACHE): [listener]])
    }
}
