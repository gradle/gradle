/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestOutputEventListener
import org.gradle.util.SetSystemProperties
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

class LoggingDeprecatedFeatureHandlerTest extends Specification {
    final outputEventListener = new TestOutputEventListener()
    @Rule final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    final locationReporter = Mock(UsageLocationReporter)
    final handler = new LoggingDeprecatedFeatureHandler(locationReporter)

    def "logs each deprecation warning once only"() {
        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature1", LoggingDeprecatedFeatureHandlerTest))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature2", LoggingDeprecatedFeatureHandlerTest))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature2", LoggingDeprecatedFeatureHandlerTest))

        then:
        outputEventListener.toString() == '[WARN feature1][WARN feature2]'
    }

    def "location reporter can prepend text"() {
        def usage = new DeprecatedFeatureUsage("feature", LoggingDeprecatedFeatureHandlerTest)

        when:
        handler.deprecatedFeatureUsed(usage)

        then:
        1 * locationReporter.reportLocation(_, _) >> { DeprecatedFeatureUsage param1, StringBuilder message ->
            message.append("location")
        }

        and:
        outputEventListener.toString() == TextUtil.toPlatformLineSeparators('[WARN location\nfeature]')
    }
}
