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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.util.SetSystemProperties
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(LoggingDeprecatedFeatureHandler)
class LoggingDeprecatedFeatureHandlerTest extends Specification {
    final outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()
    final locationReporter = Mock(UsageLocationReporter)
    final handler = new LoggingDeprecatedFeatureHandler(locationReporter)

    def 'logs each deprecation warning only once'() {
        when:
        handler.deprecatedFeatureUsed(deprecatedFeatureUsage('feature1'))
        handler.deprecatedFeatureUsed(deprecatedFeatureUsage('feature2'))
        handler.deprecatedFeatureUsed(deprecatedFeatureUsage('feature2'))

        then:
        def events = outputEventListener.events
        events.size() == 2

        and:
        events[0].message == 'feature1'
        events[1].message == 'feature2'
    }

    def 'deprecations are logged at WARN level'() {
        when:
        handler.deprecatedFeatureUsed(deprecatedFeatureUsage('feature'))

        then:
        outputEventListener.events.size() == 1

        and:
        def event = outputEventListener.events[0]
        event.message == 'feature'
        event.logLevel == LogLevel.WARN
    }

    @Unroll
    def 'fake call with #deprecationTracePropertyName=true logs full stack trace.'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'true')

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake'), fakeStackTrace))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake\n'))
        message.contains('some.GradleScript.foo')

        message.contains('SimulatedJavaCallLocation.create')
        message.count('java.lang.reflect.Method.invoke') == 3
        message.contains('some.Class.withoutSource')
        message.contains('some.Class.withNativeMethod')
        message.contains('some.KotlinGradleScript')

        where:
        fakeStackTrace = [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('some.GradleScript', 'foo', 'GradleScript.gradle', 1337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    @Unroll
    def 'fake call with Gradle script element first and #deprecationTracePropertyName=false logs only Gradle script element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake'), fakeStackTrace))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake\n'))
        message.contains('some.GradleScript.foo')

        !message.contains('SimulatedJavaCallLocation.create')
        !message.contains('java.lang.reflect.Method.invoke')
        !message.contains('some.Class.withoutSource')
        !message.contains('some.Class.withNativeMethod')
        !message.contains('some.KotlinGradleScript')

        where:
        fakeStackTrace = [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('some.GradleScript', 'foo', 'GradleScript.gradle', 1337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    @Unroll
    def 'fake call with Gradle Kotlin script element first and #deprecationTracePropertyName=false logs only Gradle Kotlin script element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake'), fakeStackTrace))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake\n'))
        message.contains('some.KotlinGradleScript')

        !message.contains('SimulatedJavaCallLocation.create')
        !message.contains('java.lang.reflect.Method.invoke')
        !message.contains('some.Class.withoutSource')
        !message.contains('some.Class.withNativeMethod')
        !message.contains('some.GradleScript.foo')

        where:
        fakeStackTrace = [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.GradleScript', 'foo', 'GradleScript.gradle', 1337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    @Unroll
    def 'fake call without Gradle script elements and #deprecationTracePropertyName=false does not log a stack trace element.'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake'), fakeStackTrace))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message == 'fake'

        where:
        fakeStackTrace = [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('some.ArbitraryClass', 'withSource', 'ArbitraryClass.java', 42),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    @Unroll
    def 'fake call with only a single stack trace element and #deprecationTracePropertyName=false does not log that element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake'), fakeStackTrace))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message == 'fake'

        where:
        fakeStackTrace = [
            new StackTraceElement('some.ArbitraryClass', 'withSource', 'ArbitraryClass.java', 42),
        ]
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    @Unroll
    def 'fake call with only a single stack trace element and #deprecationTracePropertyName=true logs that element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'true')

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(new DeprecatedFeatureUsage('fake', DeprecatedFeatureUsageTest), fakeStackTrace))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake\n'))
        message.contains('some.ArbitraryClass.withSource')

        where:
        fakeStackTrace = [
            new StackTraceElement('some.ArbitraryClass', 'withSource', 'ArbitraryClass.java', 42),
        ]
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    @Unroll
    def 'fake call without stack trace elements and #deprecationTracePropertyName=#deprecationTraceProperty logs only message'() {
        given:
        System.setProperty(deprecationTracePropertyName, '' + deprecationTraceProperty)

        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage('fake', DeprecatedFeatureUsageTest))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        events[0].message == 'fake'

        where:
        deprecationTraceProperty << [true, false]
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    def 'location reporter can prepend text'() {
        when:
        handler.deprecatedFeatureUsed(deprecatedFeatureUsage('feature'))

        then:
        1 * locationReporter.reportLocation(_, _) >> { DeprecatedFeatureUsage param1, StringBuilder message ->
            message.append('location')
        }

        and:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == TextUtil.toPlatformLineSeparators('location\nfeature')
    }

    @Unroll
    def 'Setting #deprecationTracePropertyName=#deprecationTraceProperty overrides setTraceLoggingEnabled value.'() {
        given:
        System.setProperty(deprecationTracePropertyName, deprecationTraceProperty)

        when:
        LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(true)

        then:
        LoggingDeprecatedFeatureHandler.isTraceLoggingEnabled() == expectedResult

        when:
        LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(false)

        then:
        LoggingDeprecatedFeatureHandler.isTraceLoggingEnabled() == expectedResult

        where:
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
        deprecationTraceProperty << ['true', 'false', 'foo']
        expectedResult << [true, false, false]
    }

    @Unroll
    def 'Undefined #deprecationTracePropertyName does not influence setTraceLoggingEnabled value.'() {
        given:
        System.clearProperty(deprecationTracePropertyName)

        when:
        LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(true)

        then:
        LoggingDeprecatedFeatureHandler.isTraceLoggingEnabled()

        when:
        LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(false)

        then:
        !LoggingDeprecatedFeatureHandler.isTraceLoggingEnabled()

        where:
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    private static DeprecatedFeatureUsage deprecatedFeatureUsage(String message) {
        new DeprecatedFeatureUsage(message, LoggingDeprecatedFeatureHandlerTest)
    }
}
