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
import org.gradle.logging.TestAppender
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class LoggingDeprecatedFeatureHandlerTest extends Specification {
    final appender = new TestAppender()
    @Rule final ConfigureLogging logging = new ConfigureLogging(appender)
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    final handler = new LoggingDeprecatedFeatureHandler()

    def "logs each deprecation warning once only"() {
        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature1", LoggingDeprecatedFeatureHandlerTest))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature2", LoggingDeprecatedFeatureHandlerTest))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature2", LoggingDeprecatedFeatureHandlerTest))

        then:
        appender.toString() == '[WARN feature1][WARN feature2]'
    }
}
