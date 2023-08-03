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

package org.gradle.internal.deprecation

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.Describables
import org.gradle.internal.featurelifecycle.DeprecatedUsageProgressDetails
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.internal.featurelifecycle.StackTraceSanitizerTest
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.DefaultBuildOperationProgressEventEmitter
import org.gradle.internal.operations.DefaultBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.time.Clock
import org.gradle.problems.Location
import org.gradle.problems.ProblemDiagnostics
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(LoggingDeprecatedFeatureHandler)
class LoggingDeprecatedFeatureHandlerTest extends Specification {
    final outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()
    final problemStream = Mock(ProblemStream)
    final diagnosticsFactory = Stub(ProblemDiagnosticsFactory)
    final handler = new LoggingDeprecatedFeatureHandler()
    final Clock clock = Mock(Clock)
    final BuildOperationListener buildOperationListener = Mock()
    final CurrentBuildOperationRef currentBuildOperationRef = new CurrentBuildOperationRef()
    final BuildOperationProgressEventEmitter progressBroadcaster = new DefaultBuildOperationProgressEventEmitter(
        clock, currentBuildOperationRef, buildOperationListener
    )

    def setup() {
        _ * diagnosticsFactory.newStream() >> problemStream
        _ * diagnosticsFactory.newUnlimitedStream() >> problemStream
        handler.init(diagnosticsFactory, WarningMode.All, progressBroadcaster)
    }

    def 'logs each deprecation warning only once'() {
        given:
        useStackTrace()
        useStackTrace()
        useStackTrace()

        when:
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature2'))
        handler.featureUsed(deprecatedFeatureUsage('feature2'))

        then:
        def events = outputEventListener.events
        events.size() == 2

        and:
        events[0].message == 'feature1 removal'
        events[1].message == 'feature2 removal'
    }

    def 'deprecations are logged at WARN level'() {
        given:
        useStackTrace()

        when:
        handler.featureUsed(deprecatedFeatureUsage('feature'))

        then:
        outputEventListener.events.size() == 1

        and:
        def event = outputEventListener.events[0]
        event.message == 'feature removal'
        event.logLevel == LogLevel.WARN
    }

    def 'includes problem location in message'() {
        given:
        useLocation("<long>", 123)

        when:
        handler.featureUsed(deprecatedFeatureUsage('feature'))

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == TextUtil.toPlatformLineSeparators('<long>: line 123\nfeature removal')
    }

    def 'logs deprecation warning once for each unique Gradle script location that appears first in the stack trace'() {
        given:
        useStackTrace(fakeStackTrace1)
        useStackTrace(fakeStackTrace2)
        useStackTrace(fakeStackTrace1)
        useStackTrace(fakeStackTrace2)

        when:
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))

        then:
        def events = outputEventListener.events
        events.size() == 2

        and:
        events[0].message == TextUtil.toPlatformLineSeparators("""feature1 removal
\tat some.KotlinGradleScript.foo(GradleScript.gradle.kts:31337)
\t(Run with --stacktrace to get the full stack trace of this deprecation warning.)""")
        events[1].message == TextUtil.toPlatformLineSeparators("""feature1 removal
\tat some.KotlinGradleScript.foo(GradleScript.gradle.kts:7)
\t(Run with --stacktrace to get the full stack trace of this deprecation warning.)""")

        where:
        fakeStackTrace1 = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 15),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
        fakeStackTrace2 = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 15),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 7),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
    }

    def 'logs deprecation warning once for each unique location'() {
        given:
        useLocation("<one>", 123)
        useLocation("<one>", 1)
        useLocation("<one>", 123)
        useLocation("<one>", 1)

        when:
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))

        then:
        def events = outputEventListener.events
        events.size() == 2

        and:
        events[0].message == TextUtil.toPlatformLineSeparators("""<one>: line 123
feature1 removal""")
        events[1].message == TextUtil.toPlatformLineSeparators("""<one>: line 1
feature1 removal""")
    }

    def 'does not log deprecation warning without stack trace if the same warning has already been seen with a stack trace'() {
        given:
        useStackTrace(fakeStackTrace)
        useLocation("<plugin>", 123, fakeStackTrace)
        useStackTrace()
        useLocation("<plugin>", 123)

        when:
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))

        then:
        def events = outputEventListener.events
        events.size() == 2

        where:
        fakeStackTrace = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 15),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ] as ArrayList<StackTraceElement>
    }

    def "no warnings should be displayed in #type"() {
        when:
        handler.init(diagnosticsFactory, type, progressBroadcaster)
        handler.featureUsed(deprecatedFeatureUsage('feature1'))

        then:
        outputEventListener.events.empty

        where:
        type << WarningMode.values().findAll { !it.shouldDisplayMessages() }
    }

    def 'fake call with #deprecationTracePropertyName=true logs full stack trace.'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'true')
        useStackTrace(fakeStackTrace)

        when:
        handler.featureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake')))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake removal\n'))
        message.contains('some.GradleScript.foo')

        message.contains('SimulatedJavaCallLocation.create')
        message.count('java.lang.reflect.Method.invoke') == 3
        message.contains('some.Class.withoutSource')
        message.contains('some.Class.withNativeMethod')
        message.contains('some.KotlinGradleScript')

        where:
        fakeStackTrace = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 25),
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

    def 'fake call with Gradle script element first and #deprecationTracePropertyName=false logs only Gradle script element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')
        useStackTrace(fakeStackTrace)

        when:
        handler.featureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake')))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake removal\n'))
        message.contains('some.GradleScript.foo')

        !message.contains('SimulatedJavaCallLocation.create')
        !message.contains('java.lang.reflect.Method.invoke')
        !message.contains('some.Class.withoutSource')
        !message.contains('some.Class.withNativeMethod')
        !message.contains('some.KotlinGradleScript')

        where:
        fakeStackTrace = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 15),
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

    def 'fake call with Gradle Kotlin script element first and #deprecationTracePropertyName=false logs only Gradle Kotlin script element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')
        useStackTrace(fakeStackTrace)

        when:
        handler.featureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake')))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake removal\n'))
        message.contains('some.KotlinGradleScript')

        !message.contains('SimulatedJavaCallLocation.create')
        !message.contains('java.lang.reflect.Method.invoke')
        !message.contains('some.Class.withoutSource')
        !message.contains('some.Class.withNativeMethod')
        !message.contains('some.GradleScript.foo')

        where:
        fakeStackTrace = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 25),
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

    def 'fake call without Gradle script elements and #deprecationTracePropertyName=false does not log a stack trace element.'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')
        useStackTrace(fakeStackTrace)

        when:
        handler.featureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake')))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message == 'fake removal'

        where:
        fakeStackTrace = [
            new StackTraceElement(LoggingDeprecatedFeatureHandlerTest.name, 'foo', 'LoggingDeprecatedFeatureHandlerTest.java', 25),
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

    def 'fake call with only a single stack trace element and #deprecationTracePropertyName=false does not log that element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'false')
        useStackTrace(fakeStackTrace)

        when:
        handler.featureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('fake')))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message == 'fake removal'

        where:
        fakeStackTrace = [
            new StackTraceElement('some.ArbitraryClass', 'withSource', 'ArbitraryClass.java', 42),
        ]
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    def 'fake call with only a single stack non root trace element and #deprecationTracePropertyName=true logs that element'() {
        given:
        System.setProperty(deprecationTracePropertyName, 'true')
        useStackTrace(fakeStackTrace)

        when:
        handler.featureUsed(new DeprecatedFeatureUsage(new DeprecatedFeatureUsage('fake', "removal", null, null, Documentation.NO_DOCUMENTATION, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, StackTraceSanitizerTest)))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        def message = events[0].message

        message.startsWith(TextUtil.toPlatformLineSeparators('fake removal\n'))
        message.contains('some.ArbitraryClass.withSource')

        where:
        fakeStackTrace = [
            new StackTraceElement(StackTraceSanitizerTest.name, 'calledFrom', 'FeatureUsageTest.java', 23),
            new StackTraceElement('some.ArbitraryClass', 'withSource', 'ArbitraryClass.java', 42),
        ]
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

    def 'fake call without stack trace elements and #deprecationTracePropertyName=#deprecationTraceProperty logs only message'() {
        given:
        System.setProperty(deprecationTracePropertyName, '' + deprecationTraceProperty)
        useStackTrace()

        when:
        handler.featureUsed(new DeprecatedFeatureUsage('fake', "removal", null, null, Documentation.NO_DOCUMENTATION, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, StackTraceSanitizerTest))
        def events = outputEventListener.events

        then:
        events.size() == 1

        and:
        events[0].message == 'fake removal'

        where:
        deprecationTraceProperty << [true, false]
        deprecationTracePropertyName = LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
    }

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

    def 'deprecated usages are exposed as build operation progress events'() {
        given:
        useStackTrace()
        useStackTrace()
        useStackTrace()

        when:
        currentBuildOperationRef.set(new DefaultBuildOperationRef(new OperationIdentifier(1), null))
        handler.featureUsed(deprecatedFeatureUsage('feature1'))

        1 * buildOperationListener.progress(_, _) >> { progressFired(it[1], 'feature1') }

        handler.featureUsed(deprecatedFeatureUsage('feature2'))

        1 * buildOperationListener.progress(_, _) >> { progressFired(it[1], 'feature2') }

        handler.featureUsed(deprecatedFeatureUsage('feature2'))

        then:
        1 * buildOperationListener.progress(_, _) >> { progressFired(it[1], 'feature2') }
    }

    private void useStackTrace(List<StackTraceElement> stackTrace = []) {
        1 * problemStream.forCurrentCaller(_) >> Stub(ProblemDiagnostics) {
            _ * getLocation() >> null
            _ * getStack() >> stackTrace
        }
    }

    private void useLocation(String displayName, int lineNumber, List<StackTraceElement> stackTrace = []) {
        1 * problemStream.forCurrentCaller(_) >> Stub(ProblemDiagnostics) {
            _ * getStack() >> stackTrace
            _ * getLocation() >> new Location(Describables.of(displayName), Describables.of("<short>"), lineNumber)
        }
    }

    private static void progressFired(OperationProgressEvent progressEvent, String summary) {
        assert progressEvent.details instanceof DeprecatedUsageProgressDetails
        progressEvent.details.summary == summary
        progressEvent.details.stackTrace.size() > 0
    }

    private static DeprecatedFeatureUsage deprecatedFeatureUsage(String summary, Class<?> calledFrom = LoggingDeprecatedFeatureHandlerTest) {
        new DeprecatedFeatureUsage(summary, "removal", null, null, Documentation.NO_DOCUMENTATION, DeprecatedFeatureUsage.Type.USER_CODE_DIRECT, calledFrom)
    }
}
