/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.util

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadaster
import org.gradle.internal.featurelifecycle.UsageLocationReporter
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.junit.Rule
import spock.lang.Specification

class DeprecationLoggerTest extends Specification{

    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    final String nextGradleVersion = GradleVersion.current().nextMajor.version;

    def setup() {
        SingleMessageLogger.init(Mock(UsageLocationReporter), WarningMode.All, Mock(DeprecatedUsageBuildOperationProgressBroadaster))
    }

    def cleanup() {
        SingleMessageLogger.reset()
    }

    def "logs deprecation message"() {
        when:
        DeprecationLogger.nagUserWith(new DeprecationMessage("summary", "removal"), DeprecatedFeatureUsage.Type.USER_CODE_DIRECT)

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal'
    }

    def "logs deprecation message with advice"() {
        when:
        DeprecationLogger.nagUserWith(new DeprecationMessage("summary", "removal").withAdvice("advice"), DeprecatedFeatureUsage.Type.USER_CODE_DIRECT)

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal advice'
    }

    def "logs deprecation message with contextual advice"() {
        when:
        DeprecationLogger.nagUserWith(new DeprecationMessage("summary", "removal").withContextualAdvice("contextualAdvice"), DeprecatedFeatureUsage.Type.USER_CODE_DIRECT)

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal contextualAdvice'
    }

    def "logs deprecation message with advice and contextual advice"() {
        when:
        DeprecationLogger.nagUserWith(new DeprecationMessage("summary", "removal")
            .withAdvice("advice").withContextualAdvice("contextualAdvice"), DeprecatedFeatureUsage.Type.USER_CODE_DIRECT)

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal contextualAdvice advice'
    }

    def "logs generic deprecation message"() {
        when:
        DeprecationLogger.nagUserOfDeprecatedThing("Summary.", "Advice.")

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Summary. This has been deprecated and is scheduled to be removed in Gradle ${nextGradleVersion}. Advice."
    }
}
