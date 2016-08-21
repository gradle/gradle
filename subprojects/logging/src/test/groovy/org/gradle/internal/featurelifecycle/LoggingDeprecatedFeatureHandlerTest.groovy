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

import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.util.SetSystemProperties
import org.gradle.util.SingleMessageLogger
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(LoggingDeprecatedFeatureHandler)
class LoggingDeprecatedFeatureHandlerTest extends Specification {
    final outputEventListener = new CollectingTestOutputEventListener()
    @Rule final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    final locationReporter = Mock(UsageLocationReporter)
    final handler = new LoggingDeprecatedFeatureHandler(locationReporter)

    def "logs each deprecation warning once only"() {
        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature1", []))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature2", []))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature2", []))

        then:
        def events = outputEventListener.events
        events.size() == 2

        and:
        events[0].message == 'feature1'
        events[1].message == 'feature2'
    }

    @Unroll
    def 'logs fake call with #deprecationTracePropertyName=#deprecationTraceProperty'() {
        System.setProperty(deprecationTracePropertyName, ''+deprecationTraceProperty)

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage('fake', createFakeStackTrace()))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message
        //println message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake\n'))
        message.contains('SimulatedJavaCallLocation.create')
        message.contains('some.GradleScript.foo')
        message.contains('some.KotlinGradleScript')
        if(deprecationTraceProperty) {
            assert message.contains('java.lang.reflect.Method.invoke')
            assert message.contains('some.Class.withoutSource')
            assert message.contains('some.Class.withNativeMethod')
        } else {
            assert !message.contains('java.lang.reflect.Method.invoke')
            assert !message.contains('some.Class.withoutSource')
            assert !message.contains('some.Class.withNativeMethod')
        }

        where:
        deprecationTraceProperty << [true, false]
        deprecationTracePropertyName = SingleMessageLogger.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    List<StackTraceElement> createFakeStackTrace() {
        [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('some.GradleScript', 'foo', 'GradleScript.gradle', 1337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
    }

    def "location reporter can prepend text"() {
        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage("feature", []))

        then:
        1 * locationReporter.reportLocation(_, _) >> { DeprecatedFeatureUsage param1, StringBuilder message ->
            message.append("location")
        }

        and:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == TextUtil.toPlatformLineSeparators('location\nfeature')
    }
}
