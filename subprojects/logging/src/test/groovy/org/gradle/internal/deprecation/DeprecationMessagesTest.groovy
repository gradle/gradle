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
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadcaster
import org.gradle.internal.featurelifecycle.UsageLocationReporter
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DeprecationMessagesTest extends Specification {

    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    static final String NEXT_GRADLE_VERSION = GradleVersion.current().nextMajor.version

    def setup() {
        DeprecationLogger.init(Mock(UsageLocationReporter), WarningMode.All, Mock(DeprecatedUsageBuildOperationProgressBroadcaster))
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    def "logs deprecation message"() {
        when:
        new DeprecationMessage.Builder().withSummary("summary").withRemovalDetails("removal").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal'
    }

    def "logs deprecation message with advice"() {
        when:
        new DeprecationMessage.Builder().withSummary("summary").withRemovalDetails("removal")
            .withAdvice("advice")
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal advice'
    }

    def "logs deprecation message with contextual advice"() {
        when:
        new DeprecationMessage.Builder().withSummary("summary").withRemovalDetails("removal")
            .withContextualAdvice("contextualAdvice")
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal contextualAdvice'
    }

    def "logs deprecation message with advice and contextual advice"() {
        when:
        new DeprecationMessage.Builder().withSummary("summary").withRemovalDetails("removal")
            .withAdvice("advice").withContextualAdvice("contextualAdvice")
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal contextualAdvice advice'
    }

    def "logs generic deprecation message"() {
        when:
        DeprecationMessage.thisHasBeenDeprecated("Summary.").withAdvice("Advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Summary. This has been deprecated and is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs generic deprecation message for specific thing"() {
        when:
        DeprecationMessage.specificThingHasBeenDeprecated("Something").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Something has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}."
    }

    def "logs deprecated behaviour message"() {
        when:
        DeprecationLogger.nagUserOfDeprecatedBehaviour("Some behaviour.")

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Some behaviour. This behaviour has been deprecated and is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}."
    }

    def "logs deprecated indirect user code cause message"() {
        when:
        DeprecationMessage.specificThingHasBeenDeprecated("Something").withAdvice("Advice.").withContextualAdvice("Contextual advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Something has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Contextual advice. Advice."
    }

    def "logs deprecated build invocation message"() {
        when:
        DeprecationMessage.deprecatedBuildInvocationFeature("Feature").withAdvice("Advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Feature has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs deprecated and replaced parameter usage message"() {
        when:
        DeprecationLogger.deprecateNamedParameter("paramName").replaceWith("replacement").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The paramName named parameter has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement named parameter instead."
    }

    def "logs deprecated property message"() {
        when:
        DeprecationLogger.deprecateProperty("propertyName").withAdvice("Advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The propertyName property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs deprecated and replaced property message"() {
        when:
        DeprecationLogger.deprecateProperty("propertyName").replaceWith("replacement").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The propertyName property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement property instead."
    }

    def "logs discontinued method message"() {
        when:
        DeprecationLogger.deprecateMethod("method()").withAdvice("Advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The method() method has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs replaced method message"() {
        when:
        DeprecationLogger.deprecateMethod("method()").replaceWith("replacementMethod()").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The method() method has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementMethod() method instead."
    }

    def "logs discontinued invocation message"() {
        when:
        DeprecationMessage.discontinuedInvocation("method()").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}."
    }

    def "logs deprecated method invocation message"() {
        when:
        DeprecationLogger.deprecateInvocation("method()").withAdvice("Advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Using method method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs replaced method invocation message"() {
        when:
        DeprecationLogger.deprecateInvocation("method()").replaceWith("replacementMethod()").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Using method method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementMethod() method instead."
    }

    def "logs replaced task"() {
        when:
        DeprecationLogger.deprecateTask("taskName").replaceWith("replacementTask").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The taskName task has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementTask task instead."
    }

    def "logs deprecated tool replaced with external one"() {
        when:
        DeprecationMessage.toolReplacedWithExternalOne("toolName", "replacement").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The toolName has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consider using replacement instead."
    }

    def "logs replaced plugin message"() {
        when:
        DeprecationMessage.replacedPlugin("pluginName", "replacement").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement plugin instead."
    }

    def "logs plugin replaced with external one message"() {
        when:
        DeprecationMessage.pluginReplacedWithExternalOne("pluginName", "replacement").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consider using the replacement plugin instead."
    }

    def "logs deprecated plugin message"() {
        when:
        DeprecationMessage.deprecatedPlugin("pluginName").withAdvice("Advice.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs deprecated plugin message with link to upgrade guide"() {
        when:
        DeprecationMessage.deprecatedPlugin("pluginName", 42, "upgradeGuideSection.").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_42.html#upgradeGuideSection."
    }

    @Unroll
    def "logs deprecation message for deprecated configuration with #deprecationType deprecation"() {
        given:
        DeprecationMessage.replacedConfiguration("ConfigurationType", deprecationType, ['r1', 'r2', 'r3']).nagUser()
        def events = outputEventListener.events

        expect:
        events.size() == 1
        events[0].message == expectedMessage

        where:
        deprecationType                                     | expectedMessage
        ConfigurationDeprecationType.ARTIFACT_DECLARATION   | "The ConfigurationType configuration has been deprecated for artifact declaration. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the r1 or r2 or r3 configuration instead."
        ConfigurationDeprecationType.CONSUMPTION            | "The ConfigurationType configuration has been deprecated for consumption. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use attributes to consume the r1 or r2 or r3 configuration instead."
        ConfigurationDeprecationType.DEPENDENCY_DECLARATION | "The ConfigurationType configuration has been deprecated for dependency declaration. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the r1 or r2 or r3 configuration instead."
        ConfigurationDeprecationType.RESOLUTION             | "The ConfigurationType configuration has been deprecated for resolution. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please resolve the r1 or r2 or r3 configuration instead."
    }

}
