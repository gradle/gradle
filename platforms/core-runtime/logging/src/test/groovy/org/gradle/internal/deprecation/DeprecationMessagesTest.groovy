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
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.GradleVersion
import org.gradle.util.TestProblems
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION
import static org.gradle.internal.deprecation.DeprecationMessageBuilder.createDefaultDeprecationId

class DeprecationMessagesTest extends Specification {

    private static final String NEXT_GRADLE_VERSION = "10.0"
    private static final DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    private final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    private final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    private TestProblems problemsService

    def setup() {
        def diagnosticsFactory = new NoOpProblemDiagnosticsFactory()
        problemsService = TestUtil.problemsService()
        problemsService.recordEmittedProblems()
        def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)
        DeprecationLogger.init(WarningMode.All, buildOperationProgressEventEmitter, problemsService, diagnosticsFactory.newUnlimitedStream())
    }

    def cleanup() {
        DeprecationLogger.reset()
        problemsService.resetRecordedProblems()
    }


    def summary = "Summary is deprecated."

    def "logs deprecation message with default problem id"() {
        given:
        def builder = new DeprecationMessageBuilder()
        builder.setSummary(summary)

        when:
        builder.willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "$summary This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."

        problemsService.assertProblemEmittedOnce({ it.definition.id.displayName == 'Summary is deprecated.' })
    }

    def "logs deprecation message with custom problem id"() {
        def deprecationDisplayName = "summary deprecation"
        given:
        def builder = new DeprecationMessageBuilder()
        builder.setSummary(summary)
        builder.withProblemIdDisplayName(deprecationDisplayName)

        when:
        builder.willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "$summary This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."

        problemsService.assertProblemEmittedOnce({ it.definition.id.displayName == 'summary deprecation' })
    }

    def "logs deprecation message with advice"() {
        given:
        def builder = new DeprecationMessageBuilder()
        builder.setSummary("Summary is deprecated.")
        builder.withAdvice("Advice.")

        when:
        builder.willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Summary is deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecation message with contextual advice"() {
        given:
        def builder = new DeprecationMessageBuilder()
        builder.setSummary("Summary is deprecated.")
        builder.withContext("Context.")

        when:
        builder.willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Summary is deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Context. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecation message with advice and contextual advice"() {
        given:
        def builder = new DeprecationMessageBuilder()
        builder.setSummary("Summary is deprecated.")
        builder.withAdvice("Advice.")
        builder.withContext("Context.")

        when:
        builder.willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Summary is deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Context. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs generic deprecation message for specific thing"() {
        when:
        DeprecationLogger.deprecate("Something").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Something has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated behavior message"() {
        when:
        DeprecationLogger.deprecateBehaviour("Some behavior.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Some behavior. This behavior has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated indirect user code cause message"() {
        when:
        DeprecationLogger.deprecate("Something").withAdvice("Advice.").withContext("Contextual advice.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Something has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Contextual advice. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated build invocation message"() {
        when:
        DeprecationLogger.deprecateBuildInvocationFeature("Feature").withAdvice("Advice.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Feature has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated and replaced parameter usage message"() {
        when:
        DeprecationLogger.deprecateNamedParameter("paramName").replaceWith("replacement").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The paramName named parameter has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement named parameter instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated property message"() {
        when:
        DeprecationLogger.deprecateProperty(DeprecationLogger, "propertyName").withAdvice("Advice.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The DeprecationLogger.propertyName property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated and replaced property message"() {
        when:
        DeprecationLogger.deprecateProperty(DeprecationLogger, "propertyName").replaceWith("replacement").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The DeprecationLogger.propertyName property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement property instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated system property message"() {
        when:
        DeprecationLogger.deprecateSystemProperty("org.gradle.test").withAdvice("Advice.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The org.gradle.test system property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated and replaced system property message"() {
        when:
        DeprecationLogger.deprecateSystemProperty("org.gradle.deprecated.test").replaceWith("org.gradle.test").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The org.gradle.deprecated.test system property has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the org.gradle.test system property instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs discontinued method message"() {
        when:
        DeprecationLogger.deprecateMethod(DeprecationLogger, "method()").withAdvice("Advice.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The DeprecationLogger.method() method has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs replaced method message"() {
        when:
        DeprecationLogger.deprecateMethod(DeprecationLogger, "method()").replaceWith("replacementMethod()").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The DeprecationLogger.method() method has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementMethod() method instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs discontinued invocation message"() {
        when:
        DeprecationLogger.deprecateAction("Some action").willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Some action has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated method invocation message"() {
        when:
        DeprecationLogger.deprecateInvocation("method()").withAdvice("Advice.").willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Using method method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs replaced method invocation message"() {
        when:
        DeprecationLogger.deprecateInvocation("method()").replaceWith("replacementMethod()").willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "Using method method() has been deprecated. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementMethod() method instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs replaced task"() {
        when:
        DeprecationLogger.deprecateTask("taskName").replaceWith("replacementTask").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The taskName task has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacementTask task instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs plugin replaced with external one message"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").replaceWithExternalPlugin("replacement").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consider using the replacement plugin instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated plugin message"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").withAdvice("Advice.").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Advice. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs replaced plugin message"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").replaceWith("replacement").willBeRemovedInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use the replacement plugin instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs deprecated plugin message with link to upgrade guide"() {
        when:
        DeprecationLogger.deprecatePlugin("pluginName").willBeRemovedInGradle10().withUpgradeGuideSection(42, "upgradeGuideSection").nagUser()

        then:
        expectMessage "The pluginName plugin has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_42.html#upgradeGuideSection"
    }

    def "logs configuration deprecation message for artifact declaration"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forArtifactDeclaration().replaceWith(['r1', 'r2', 'r3']).willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The ConfigurationType configuration has been deprecated for artifact declaration. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the r1 or r2 or r3 configuration instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs configuration deprecation message for consumption"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forConsumption().replaceWith(['r1', 'r2', 'r3']).willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The ConfigurationType configuration has been deprecated for consumption. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use attributes to consume the r1 or r2 or r3 configuration instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs configuration deprecation message for dependency declaration"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forDependencyDeclaration().replaceWith(['r1', 'r2', 'r3']).willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The ConfigurationType configuration has been deprecated for dependency declaration. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please use the r1 or r2 or r3 configuration instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs configuration deprecation message for resolution"() {
        when:
        DeprecationLogger.deprecateConfiguration("ConfigurationType").forResolution().replaceWith(['r1', 'r2', 'r3']).willBecomeAnErrorInGradle10().withUserManual("feature_lifecycle", "sec:deprecated").nagUser()

        then:
        expectMessage "The ConfigurationType configuration has been deprecated for resolution. This will fail with an error in Gradle ${NEXT_GRADLE_VERSION}. Please resolve the r1 or r2 or r3 configuration instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs internal API deprecation message"() {
        when:
        DeprecationLogger.deprecateInternalApi("constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator)")
            .replaceWith("ObjectFactory.polymorphicDomainObjectContainer(Class<T>)")
            .willBeRemovedInGradle10()
            .withUserManual("feature_lifecycle", "sec:deprecated")
            .nagUser()

        then:
        expectMessage "Internal API constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator) has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use ObjectFactory.polymorphicDomainObjectContainer(Class<T>) instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "can not overwrite advice when replacing"() {
        when:
        DeprecationLogger.deprecateInternalApi("constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator)")
            .replaceWith("ObjectFactory.polymorphicDomainObjectContainer(Class<T>)")
            .withAdvice("foobar")
            .willBeRemovedInGradle10()
            .withUserManual("feature_lifecycle", "sec:deprecated")
            .nagUser()

        then:
        expectMessage "Internal API constructor DefaultPolymorphicDomainObjectContainer(Class<T>, Instantiator) has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. Please use ObjectFactory.polymorphicDomainObjectContainer(Class<T>) instead. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/feature_lifecycle.html#sec:deprecated in the Gradle documentation."
    }

    def "logs documentation reference"() {
        when:
        DeprecationLogger.deprecateBehaviour("Some behavior.")
            .willBeRemovedInGradle10()
            .withUserManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
            .nagUser()

        then:
        def expectedDocumentationUrl = DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("information", "viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
        expectMessage "Some behavior. This behavior has been deprecated. This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. ${expectedDocumentationUrl}"
    }

    def "logs DSL property documentation reference"() {
        when:
        DeprecationLogger.deprecateProperty(DeprecationLogger, "archiveName").replaceWith("archiveFileName")
            .willBeRemovedInGradle10()
            .withDslReference(AbstractArchiveTask, "bar")
            .nagUser()

        then:
        def dslReference = DOCUMENTATION_REGISTRY.getDslRefForProperty(AbstractArchiveTask, "bar")
        expectMessage "The DeprecationLogger.archiveName property has been deprecated. " +
            "This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. " +
            "Please use the archiveFileName property instead. " +
            String.format(RECOMMENDATION, "information", dslReference)
    }

    def "logs DSL documentation reference for deprecated property implicitly"() {
        when:
        DeprecationLogger.deprecateProperty(AbstractArchiveTask, "archiveName").replaceWith("archiveFileName")
            .willBeRemovedInGradle10()
            .withDslReference()
            .nagUser()

        then:
        def dslReference = DOCUMENTATION_REGISTRY.getDslRefForProperty(AbstractArchiveTask, "archiveName")
        expectMessage "The AbstractArchiveTask.archiveName property has been deprecated. " +
            "This is scheduled to be removed in Gradle ${NEXT_GRADLE_VERSION}. " +
            "Please use the archiveFileName property instead. " +
            String.format(RECOMMENDATION, "information", dslReference)
    }

    def "createDefaultDeprecationId should return cleaned id"() {
        expect:
        createDefaultDeprecationId(input) == expected

        where:
        input                                                                           | expected
        "summary"                                                                       | "summary"
        "The detachedConfiguration1 configuration has been deprecated for consumption." | "the-detachedconfiguration-configuration-has-been-deprecated-for-consumption"
    }

    private void expectMessage(String expectedMessage) {
        def events = outputEventListener.events.findAll {it.logLevel == LogLevel.WARN }
        events.size() == 1
        assert events[0].message == expectedMessage
    }
}
