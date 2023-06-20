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


import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.Factory
import org.gradle.internal.featurelifecycle.NoOpProblemDiagnosticsFactory
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.internal.DefaultGradleVersion
import org.junit.Rule
import spock.lang.Subject

@Subject(DeprecationLogger)
class DeprecationLoggerTest extends ConcurrentSpec {
    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    final diagnosticsFactory = new NoOpProblemDiagnosticsFactory()

    def setup() {
        DeprecationLogger.init(diagnosticsFactory, WarningMode.All, Mock(BuildOperationProgressEventEmitter))
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    def "logs deprecation warning once until reset"() {
        when:
        DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()
        DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith('nag')

        when:
        DeprecationLogger.reset()
        DeprecationLogger.init(diagnosticsFactory, WarningMode.All, Mock(BuildOperationProgressEventEmitter))
        DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()

        then:
        events.size() == 2
        events[0].message.startsWith('nag')
        events[1].message.startsWith('nag')
    }

    def "does not log warning while disabled with factory"() {
        given:
        Factory<String> factory = Mock(Factory)

        when:
        def result = DeprecationLogger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()
            return "result"
        }
        0 * _

        and:
        outputEventListener.events.empty
    }

    def "does not log warning while disabled with action"() {
        given:
        def action = Mock(Runnable)

        when:
        DeprecationLogger.whileDisabled(action)

        then:
        1 * action.run()
        0 * _

        and:
        outputEventListener.events.empty
    }

    def "nested whileDisabled call does not enable deprecation log in the outer method"() {
        when:
        DeprecationLogger.whileDisabled {
            DeprecationLogger.whileDisabled {
            }
            DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()
        }

        then:
        outputEventListener.events.empty
    }

    def "warnings are disabled for the current thread only"() {
        when:
        async {
            start {
                thread.blockUntil.disabled
                DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()
                instant.logged
            }
            start {
                DeprecationLogger.whileDisabled {
                    instant.disabled
                    DeprecationLogger.deprecate("ignored").willBeRemovedInGradle9().undocumented().nagUser()
                    thread.blockUntil.logged
                }
            }
        }

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith('nag')
    }

    def "deprecation message has next major version"() {
        when:
        DeprecationLogger.deprecate("foo")
            .withAdvice("bar.")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith("foo has been deprecated. This is scheduled to be removed in Gradle 9.0. bar.")
    }

    def "reports suppressed deprecation messages with --warning-mode summary"() {
        given:
        def documentationReference = new DocumentationRegistry().getDocumentationRecommendationFor("on this", "command_line_interface", "sec:command_line_warnings")
        DeprecationLogger.init(diagnosticsFactory, WarningMode.Summary, Mock(BuildOperationProgressEventEmitter))
        DeprecationLogger.deprecate("nag").willBeRemovedInGradle9().undocumented().nagUser()

        when:
        DeprecationLogger.reportSuppressedDeprecations()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == """
Deprecated Gradle features were used in this build, making it incompatible with ${DefaultGradleVersion.current().nextMajorVersion}.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

${documentationReference}"""
    }

}
