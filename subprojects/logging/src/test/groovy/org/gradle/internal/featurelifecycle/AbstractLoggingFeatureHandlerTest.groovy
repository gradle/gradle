/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.featurelifecycle

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractLoggingFeatureHandlerTest extends Specification {
    final outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    final FeatureHandler handler = createHandler()

    abstract FeatureHandler createHandler()

    def createFeatureUsage(String message) {
        return new FeatureUsage(message, getClass())
    }

    def 'logs each warning only once'() {
        when:
        handler.featureUsed(createFeatureUsage('feature1'))
        handler.featureUsed(createFeatureUsage('feature2'))
        handler.featureUsed(createFeatureUsage('feature2'))

        then:
        def events = outputEventListener.events
        events.size() == 2

        and:
        events[0].message.contains('feature1')
        events[1].message.contains('feature2')
    }

    def 'warnings are logged at WARN level'() {
        when:
        handler.featureUsed(createFeatureUsage('feature1'))

        then:
        outputEventListener.events.size() == 1

        and:
        def event = outputEventListener.events[0]
        event.message.contains('feature1')
        event.logLevel == LogLevel.WARN
    }
}
