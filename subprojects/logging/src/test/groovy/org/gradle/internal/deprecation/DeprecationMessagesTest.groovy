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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadcaster
import org.gradle.internal.featurelifecycle.UsageLocationReporter
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class DeprecationMessagesTest extends Specification {

    private static final String NEXT_GRADLE_VERSION = GradleVersion.current().nextMajor.version
    private static final DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    private final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()

    @Rule
    private final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        DeprecationLogger.init(Mock(UsageLocationReporter), WarningMode.All, Mock(DeprecatedUsageBuildOperationProgressBroadcaster))
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    def "logs deprecation message"() {
        given:
        def deprecationMessage = new DeprecationMessageBuilder()
        deprecationMessage.setSummary("summary")
        deprecationMessage.setRemovalDetails("removal")

        when:
        deprecationMessage.undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal'
    }

    def "logs deprecation message with advice"() {
        given:
        def deprecationMessage = new DeprecationMessageBuilder()
        deprecationMessage.setSummary("summary")
        deprecationMessage.setRemovalDetails("removal")
        deprecationMessage.withAdvice("advice")

        when:
        deprecationMessage.undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal advice'
    }

    def "logs deprecation message with contextual advice"() {
        given:
        def deprecationMessage = new DeprecationMessageBuilder()
        deprecationMessage.setSummary("summary")
        deprecationMessage.setRemovalDetails("removal")
        deprecationMessage.withContext("contextualAdvice")

        when:
        deprecationMessage.undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal contextualAdvice'
    }

    def "logs deprecation message with advice and contextual advice"() {
        given:
        def deprecationMessage = new DeprecationMessageBuilder()
        deprecationMessage.setSummary("summary")
        deprecationMessage.setRemovalDetails("removal")
        deprecationMessage.withAdvice("advice")
        deprecationMessage.withContext("contextualAdvice")

        when:
        deprecationMessage.undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == 'summary removal contextualAdvice advice'
    }

    def "logs generic deprecation message for specific thing"() {
        when:
        DeprecationLogger.deprecate("Something").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Something has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}."
    }

    def "logs deprecated behaviour message"() {
        when:
        DeprecationLogger.deprecateBehaviour("Some behaviour.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Some behaviour. This behaviour has been deprecated and is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}."
    }

    def "logs deprecated indirect user code cause message"() {
        when:
        DeprecationLogger.deprecate("Something").withAdvice("Advice.").withContext("Contextual advice.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Something has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Contextual advice. Advice."
    }

    def "logs deprecated build invocation message"() {
        when:
        DeprecationLogger.deprecateBuildInvocationFeature("Feature").withAdvice("Advice.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Feature has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs deprecated and replaced parameter usage message"() {
        when:
        DeprecationLogger.deprecateNamedParameter("paramName").replaceWith("replacement").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The paramName named parameter has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement named parameter instead."
    }

    def "logs deprecated property message"() {
        when:
        DeprecationLogger.deprecateProperty("propertyName").withAdvice("Advice.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The propertyName property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs deprecated and replaced property message"() {
        when:
        DeprecationLogger.deprecateProperty("propertyName").replaceWith("replacement").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The propertyName property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement property instead."
    }

    def "logs discontinued method message"() {
        when:
        DeprecationLogger.deprecateMethod("method()").withAdvice("Advice.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The method() method has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs replaced method message"() {
        when:
        DeprecationLogger.deprecateMethod("method()").replaceWith("replacementMethod()").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The method() method has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementMethod() method instead."
    }

    def "logs discontinued invocation message"() {
        when:
        DeprecationLogger.deprecateAction("Some action").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Some action has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}."
    }

    def "logs deprecated method invocation message"() {
        when:
        DeprecationLogger.deprecateInvocation("method()").withAdvice("Advice.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Using method method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs replaced method invocation message"() {
        when:
        DeprecationLogger.deprecateInvocation("method()").replaceWith("replacementMethod()").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Using method method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementMethod() method instead."
    }

    def "logs replaced task"() {
        when:
        DeprecationLogger.deprecateTask("taskName").replaceWith("replacementTask").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The taskName task has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementTask task instead."
    }

    def "logs plugin replaced with external one message"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").replaceWithExternalPlugin("replacement").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consider using the replacement plugin instead."
    }

    def "logs deprecated plugin message"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").withAdvice("Advice.").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice."
    }

    def "logs replaced plugin message"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").replaceWith("replacement").undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement plugin instead."
    }

    def "logs deprecated plugin message with link to upgrade guide"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").withUpgradeGuideSection(42, "upgradeGuideSection").nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_42.html#upgradeGuideSection"
    }

    def "logs configuration deprecation message for artifact declaration"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forArtifactDeclaration().replaceWith(['r1', 'r2', 'r3']).undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The ConfigurationType configuration has been deprecated for artifact declaration. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the r1 or r2 or r3 configuration instead."
    }

    def "logs configuration deprecation message for consumption"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forConsumption().replaceWith(['r1', 'r2', 'r3']).undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The ConfigurationType configuration has been deprecated for consumption. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use attributes to consume the r1 or r2 or r3 configuration instead."
    }

    def "logs configuration deprecation message for dependency declaration"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forDependencyDeclaration().replaceWith(['r1', 'r2', 'r3']).undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The ConfigurationType configuration has been deprecated for dependency declaration. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the r1 or r2 or r3 configuration instead."
    }

    def "logs configuration deprecation message for resolution"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forResolution().replaceWith(['r1', 'r2', 'r3']).undocumented().nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "The ConfigurationType configuration has been deprecated for resolution. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please resolve the r1 or r2 or r3 configuration instead."
    }

    def "logs internal API deprecation message"() {
        when:
        DeprecationLogger.deprecateInternalApi("constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator)")
            .replaceWith("ObjectFactory.polymorphicDomainObjectContainer(Class<T>)")
            .undocumented()
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Internal API constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator) has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use ObjectFactory.polymorphicDomainObjectContainer(Class<T>) instead."
    }

    def "can not overwrite advice when replacing"() {
        when:
        DeprecationLogger.deprecateInternalApi("constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator)")
            .replaceWith("ObjectFactory.polymorphicDomainObjectContainer(Class<T>)")
            .withAdvice("foobar")
            .undocumented()
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Internal API constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator) has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use ObjectFactory.polymorphicDomainObjectContainer(Class<T>) instead."
    }

    def "logs deprecation without scheduled removal"() {
        when:
        DeprecationLogger.warnOfChangedBehaviour("Publication ignores 'transitive = false' at configuration level.")
            .withAdvice("Consider using 'transitive = false' at the dependency level if you need this to be published.")
            .undocumented()
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message == "Publication ignores 'transitive = false' at configuration level. Consider using 'transitive = false' at the dependency level if you need this to be published."
    }

    def "logs documentation reference"() {
        when:
        DeprecationLogger.deprecateBehaviour("Some behaviour.")
            .guidedBy("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
            .nagUser()

        then:
        def events = outputEventListener.events
        events.size() == 1
        def expectedDocumentationUrl = DOCUMENTATION_REGISTRY.getDocumentationFor("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
        events[0].message == "Some behaviour. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. See ${expectedDocumentationUrl} for more details."
    }
}
