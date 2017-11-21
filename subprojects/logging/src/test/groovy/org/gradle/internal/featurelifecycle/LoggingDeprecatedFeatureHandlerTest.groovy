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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(LoggingDeprecatedFeatureHandler)
class LoggingDeprecatedFeatureHandlerTest extends Specification {
    private static final String REPORT_LOCATION = "build/reports/deprecations/report.html"
    final outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
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
        handler.deprecationUsages.keySet() == ['feature1', 'feature2'] as Set
    }

    def assertSingleWarning() {
        assert outputEventListener.events.size() == 1
        assert outputEventListener.events[0].message.startsWith("Some deprecated APIs are used in this build")
        assert outputEventListener.events[0].logLevel == LogLevel.WARN
        return true
    }

    def renderDeprecationReport() {
        handler.renderDeprecationReport(temporaryFolder.file(REPORT_LOCATION))
    }

    def 'no warning displayed if no deprecation warnings'() {
        when:
        renderDeprecationReport()

        then:
        outputEventListener.events.empty
    }

    @Unroll
    def 'fake call logs full stack trace'() {
        when:
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('gradle'), gradleScriptStacktrace()))
        handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(deprecatedFeatureUsage('kotlin'), kotlinScriptStracktrace()))
        renderDeprecationReport()

        then:
        assertSingleWarning()

        and:
        def html = temporaryFolder.file(REPORT_LOCATION).readLines()*.trim().join('\n')

        html.contains('''
<h4 class="panel-title">
gradle
<br>
<button type="button" data-toggle="collapse" data-parent="#accordion" href="#collapse1" class="btn btn-primary">Show details</button>
</h4>
''')
        html.contains('''
<h4 class="panel-title">
kotlin
<br>
<button type="button" data-toggle="collapse" data-parent="#accordion" href="#collapse2" class="btn btn-primary">Show details</button>
</h4>
''')
        html.contains('''
<pre>
gradle
at org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation.create(SimulatedJavaCallLocation.java:25)
at java.lang.reflect.Method.invoke(Method.java:498)
at some.Class.withoutSource(Unknown Source)
at some.Class.withNativeMethod(Native Method)
at some.GradleScript.foo(GradleScript.gradle:1337)
at java.lang.reflect.Method.invoke(Method.java:498)
at some.KotlinGradleScript.foo(GradleScript.gradle.kts:31337)
at java.lang.reflect.Method.invoke(Method.java:498)
</pre>
''')
        html.contains('''
<pre>
kotlin
at org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation.create(SimulatedJavaCallLocation.java:25)
at java.lang.reflect.Method.invoke(Method.java:498)
at some.Class.withoutSource(Unknown Source)
at some.Class.withNativeMethod(Native Method)
at some.KotlinGradleScript.foo(GradleScript.gradle.kts:31337)
at java.lang.reflect.Method.invoke(Method.java:498)
at some.GradleScript.foo(GradleScript.gradle:1337)
at java.lang.reflect.Method.invoke(Method.java:498)
</pre>
''')
    }

    static gradleScriptStacktrace() {
        [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('some.GradleScript', 'foo', 'GradleScript.gradle', 1337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ]
    }

    static kotlinScriptStracktrace() {
        [
            new StackTraceElement('org.gradle.internal.featurelifecycle.SimulatedJavaCallLocation', 'create', 'SimulatedJavaCallLocation.java', 25),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.Class', 'withoutSource', null, -1),
            new StackTraceElement('some.Class', 'withNativeMethod', null, -2),
            new StackTraceElement('some.KotlinGradleScript', 'foo', 'GradleScript.gradle.kts', 31337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
            new StackTraceElement('some.GradleScript', 'foo', 'GradleScript.gradle', 1337),
            new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 498),
        ]
    }

    def 'location reporter can prepend text'() {
        when:
        handler.deprecatedFeatureUsed(deprecatedFeatureUsage('feature'))
        renderDeprecationReport()

        then:
        1 * locationReporter.reportLocation(_, _) >> { DeprecatedFeatureUsage param1, StringBuilder message ->
            message.append('location')
        }

        and:
        assertSingleWarning()
    }

    private static DeprecatedFeatureUsage deprecatedFeatureUsage(String message) {
        new DeprecatedFeatureUsage(message, LoggingDeprecatedFeatureHandlerTest)
    }
}
