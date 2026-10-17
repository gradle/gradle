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

package org.gradle.integtests.tooling.r99

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryOutcomeResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryReusedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoreFailedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoreSkippedResult
import org.gradle.tooling.events.configuration.ConfigurationCacheEntryStoredResult
import org.gradle.tooling.events.configuration.ConfigurationCacheOperationDescriptor

@ToolingApiVersion(">=9.9")
class ConfigurationCacheEntryOutcomeProgressEventCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile << """
            task ok
        """
    }

    @TargetGradleVersion(">=9.9")
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
        outcomeOperation.result instanceof ConfigurationCacheEntryStoredResult
        ((ConfigurationCacheEntryOutcomeResult) outcomeOperation.result).problemCount == 0

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
        events.operation("Configuration cache entry outcome").result instanceof ConfigurationCacheEntryReusedResult
    }

    @TargetGradleVersion(">=9.9")
    def "reports a skipped store when an incompatible task is scheduled"() {
        given:
        buildFile << """
            task incompatible {
                notCompatibleWithConfigurationCache("declarative reason")
                doLast { }
            }
        """

        when:
        def events = ProgressEvents.create()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("incompatible")
                .withArguments("--configuration-cache")
                .addProgressListener(events, OperationType.CONFIGURATION_CACHE)
                .run()
        }

        then:
        events.operation("Configuration cache entry outcome").result instanceof ConfigurationCacheEntryStoreSkippedResult
    }

    @TargetGradleVersion(">=9.9")
    def "reports a failed store with the failure that caused it"() {
        given:
        buildFile << """
            gradle.buildFinished { }
        """

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
        thrown(BuildException)
        def result = events.operation("Configuration cache entry outcome").result
        result instanceof ConfigurationCacheEntryStoreFailedResult
        with(result as ConfigurationCacheEntryStoreFailedResult) {
            problemCount == 1
            failures.size() == 1
            failures[0].message.startsWith("Configuration cache problems found in this build.")
        }
    }

    @TargetGradleVersion(">=9.9")
    def "reports a failed store without failures when the build fails before the entry is stored"() {
        given:
        buildFile << """
            throw new RuntimeException("BOOM")
        """

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
        thrown(BuildException)
        def result = events.operation("Configuration cache entry outcome").result
        result instanceof ConfigurationCacheEntryStoreFailedResult
        (result as ConfigurationCacheEntryStoreFailedResult).failures.empty
    }

    @TargetGradleVersion(">=9.9")
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
    @TargetGradleVersion(">=6.6 <9.9")
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
