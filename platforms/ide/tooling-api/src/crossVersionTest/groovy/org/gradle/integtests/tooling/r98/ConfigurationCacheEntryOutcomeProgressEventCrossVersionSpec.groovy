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

package org.gradle.integtests.tooling.r98

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryOutcomeResult
import org.gradle.tooling.events.configuration.ConfigurationCacheOperationDescriptor

@ToolingApiVersion(">=9.8")
class ConfigurationCacheEntryOutcomeProgressEventCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile << """
            task ok
        """
    }

    @TargetGradleVersion(">=9.8")
    def "generates configuration cache entry outcome events"() {
        when:
        def events = ProgressEvents.create()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("ok")
                .withArguments("--configuration-cache")
                .addProgressListener(events, OperationType.CONFIGURATION_CACHE)
                .run()
        }

        then:
        def outcomeOperation = events.operation("Configuration cache entry outcome")
        outcomeOperation.descriptor instanceof ConfigurationCacheOperationDescriptor
        with((ConfigurationCacheEntryOutcomeResult) outcomeOperation.result) {
            outcome == "STORED"
            problemCount == 0
        }

        when:
        events = ProgressEvents.create()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("ok")
                .withArguments("--configuration-cache")
                .addProgressListener(events, OperationType.CONFIGURATION_CACHE)
                .run()
        }

        then:
        ((ConfigurationCacheEntryOutcomeResult) events.operation("Configuration cache entry outcome").result).outcome == "REUSED"
    }

    @TargetGradleVersion(">=9.8")
    def "generates no configuration cache events when the configuration cache is not used"() {
        when:
        def events = ProgressEvents.create()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("ok")
                .addProgressListener(events, OperationType.CONFIGURATION_CACHE)
                .run()
        }

        then:
        events.operations.empty
    }

    // 6.6 is the first version with the --configuration-cache flag
    @TargetGradleVersion(">=6.6 <9.8")
    def "older Gradle versions do not generate configuration cache events"() {
        when:
        def events = ProgressEvents.create()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("ok")
                .withArguments("--configuration-cache")
                .addProgressListener(events, OperationType.CONFIGURATION_CACHE)
                .run()
        }

        then:
        events.operations.empty
    }
}
