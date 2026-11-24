/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedVariantSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.Describables
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Verifies how {@link AttributeMatchingArtifactVariantSelector} compares a primary variant without artifacts
 * with the other variants of its configuration, using a real attribute matcher.
 */
class ArtifactlessPrimaryAttributeMatchingArtifactVariantSelectorSpec extends Specification {
    def schema = AttributeTestUtil.immutableSchema()
    def transforms = Mock(ConsumerProvidedVariantFinder)
    def labels = [:]

    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        DeprecationLogger.reset()
        DeprecationLogger.init(WarningMode.All, Mock(BuildOperationProgressEventEmitter), TestUtil.problemsService(), new NoOpProblemDiagnosticsFactory().newUnlimitedStream())
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    def "selects #expected for request #requested from primary #primary and secondaries #secondaries with transformable #transformable (preview enabled: #previewEnabled, warns: #warns)"() {
        given:
        def producer = producerOf(primary, secondaries)
        transforms.findCandidateTransformationChains(_, _) >> { List<ResolvedVariant> sources, ImmutableAttributes target ->
            sources.findAll { it.asDescribable().displayName in transformable }.collect { new TransformedVariant(it, Stub(VariantDefinition) { getTargetAttributes() >> target }) }
        }

        when:
        def result = selector(previewEnabled).select(producer, AttributeTestUtil.attributes(requested), false)

        then:
        describe(result) == expected
        warned() == warns

        where:
        primary          | secondaries                          | requested                     | transformable  | previewEnabled | expected                 | warns
        // An equal tie is ambiguous, and an extra-attribute tie goes to the primary variant, which lacks the extra attribute
        [a: "1"]         | [[a: "1"]]                           | [a: "1"]                      | []             | false          | "secondary0"             | true
        [a: "1"]         | [[a: "1"]]                           | [a: "1"]                      | []             | true           | "failure"                | false
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1"]                      | []             | false          | "secondary0"             | true
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1"]                      | []             | true           | "primary"                | true
        // A strictly better match for the requested attributes
        [a: "1", b: "x"] | [[a: "1"]]                           | [a: "1", b: "x"]              | []             | false          | "secondary0"             | true
        [a: "1", b: "x"] | [[a: "1"]]                           | [a: "1", b: "x"]              | []             | true           | "primary"                | true
        // Nothing else matches
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | []             | false          | "failure"                | true
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | []             | true           | "primary"                | true
        // A primary variant that lacks a requested attribute still matches directly, so it beats a transformation
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1", artifactType: "txt"] | ["secondary0"] | false          | "transformed secondary0" | true
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1", artifactType: "txt"] | ["secondary0"] | true           | "primary"                | true
        // A primary variant that matches directly beats a transformation of another variant
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | ["secondary0"] | false          | "transformed secondary0" | true
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | ["secondary0"] | true           | "primary"                | true
        // Only a transformation of the primary variant matches
        [a: "1"]         | [[a: "2"]]                           | [a: "3"]                      | ["primary"]    | false          | "failure"                | true
        [a: "1"]         | [[a: "2"]]                           | [a: "3"]                      | ["primary"]    | true           | "transformed primary"    | true
        // An incompatible primary variant does not matter
        [a: "1"]         | [[a: "2"]]                           | [a: "2"]                      | []             | false          | "secondary0"             | false
        [a: "1"]         | [[a: "2"]]                           | [a: "2"]                      | []             | true           | "secondary0"             | false
        // The primary variant breaks a tie between secondary variants, since it lacks their extra attribute, but without it they stay ambiguous
        [a: "1"]         | [[a: "1", f: "x"], [a: "1", f: "y"]] | [a: "1"]                      | []             | false          | "failure"                | true
        [a: "1"]         | [[a: "1", f: "x"], [a: "1", f: "y"]] | [a: "1"]                      | []             | true           | "primary"                | true
    }

    def "selects #expected for request #requested from primary #primary and secondaries #secondaries with ambiguous transformations of #ambiguous (lenient: #lenient, preview enabled: #previewEnabled, warns: #warns)"() {
        given:
        def producer = producerOf(primary, secondaries)
        transforms.findCandidateTransformationChains(_, _) >> { List<ResolvedVariant> sources, ImmutableAttributes target ->
            if (sources.any { it.asDescribable().displayName == ambiguous }) {
                throw new ArtifactSelectionException("ambiguous transformations of " + ambiguous, Stub(ArtifactSelectionFailure), [])
            }
            []
        }

        when:
        def result = selector(previewEnabled).select(producer, AttributeTestUtil.attributes(requested), lenient)

        then:
        describe(result) == expected
        warned() == warns

        where:
        primary  | secondaries                     | requested                     | ambiguous    | lenient | previewEnabled | expected  | warns
        // Only ambiguous transformations of the primary variant match, so selecting it fails, even for a lenient request
        [a: "1"] | [[a: "2"]]                      | [a: "3"]                      | "primary"    | false   | false          | "failure" | true
        [a: "1"] | [[a: "2"]]                      | [a: "3"]                      | "primary"    | false   | true           | "failure" | false
        [a: "1"] | [[a: "2"]]                      | [a: "3"]                      | "primary"    | true    | false          | "empty"   | true
        [a: "1"] | [[a: "2"]]                      | [a: "3"]                      | "primary"    | true    | true           | "failure" | false
        // A primary variant that lacks a requested attribute still matches directly, so it beats ambiguous transformations of the other variants
        [a: "1"] | [[a: "2", artifactType: "jar"]] | [a: "1", artifactType: "txt"] | "secondary0" | false   | false          | "failure" | true
        [a: "1"] | [[a: "2", artifactType: "jar"]] | [a: "1", artifactType: "txt"] | "secondary0" | false   | true           | "primary" | true
    }

    def "selects #expected for lenient request #requested from primary #primary and secondaries #secondaries with transformable #transformable (preview enabled: #previewEnabled, warns: #warns)"() {
        given:
        def producer = producerOf(primary, secondaries)
        transforms.findCandidateTransformationChains(_, _) >> { List<ResolvedVariant> sources, ImmutableAttributes target ->
            sources.findAll { it.asDescribable().displayName in transformable }.collect { new TransformedVariant(it, Stub(VariantDefinition) { getTargetAttributes() >> target }) }
        }

        when:
        def result = selector(previewEnabled).select(producer, AttributeTestUtil.attributes(requested), true)

        then:
        describe(result) == expected
        warned() == warns

        where:
        primary  | secondaries | requested | transformable | previewEnabled | expected              | warns
        // Nothing else matches, so the result has no artifacts either way, and only selecting the primary variant warns
        [a: "1"] | [[a: "2"]]  | [a: "1"]  | []            | false          | "empty"               | false
        [a: "1"] | [[a: "2"]]  | [a: "1"]  | []            | true           | "primary"             | true
        [a: "1"] | [[a: "2"]]  | [a: "3"]  | ["primary"]   | false          | "empty"               | false
        [a: "1"] | [[a: "2"]]  | [a: "3"]  | ["primary"]   | true           | "transformed primary" | true
        // Another variant matches, so ignoring the primary variant changes the result
        [a: "1"] | [[a: "1"]]  | [a: "1"]  | []            | false          | "secondary0"          | true
    }

    def "deciding whether to warn does not fail a selection that does not involve the primary variant (lenient: #lenient)"() {
        given:
        def producer = producerOf([a: "1"], [[a: "2"]])
        transforms.findCandidateTransformationChains(_, _) >> { List<ResolvedVariant> sources, ImmutableAttributes target ->
            if (sources.any { it.isPreviouslyUnselectable() }) {
                throw new IllegalStateException("rule fails for the primary variant")
            }
            []
        }

        when:
        def result = selector(false).select(producer, AttributeTestUtil.attributes([a: "3"]), lenient)

        then:
        describe(result) == expected
        !warned()
        !failedWith(result, IllegalStateException)

        where:
        lenient | expected
        true    | "empty"
        false   | "failure"
    }

    def "warning lists the unrequested attributes of the variant selected without the primary variant that it lacks or has with another value (preview enabled: #previewEnabled)"() {
        given:
        def producer = producerOf([a: "1", b: "x", c: "z"], [[a: "1", b: "y", c: "z", artifactType: "jar"]])

        when:
        def result = selector(previewEnabled).select(producer, AttributeTestUtil.attributes([a: "1"]), false)

        then:
        describe(result) == expected
        def warning = outputEventListener.events.find { it.logLevel == LogLevel.WARN }.toString()
        warning.contains("Request one of these attributes: ")
        warning.contains("b=y")
        warning.contains("artifactType=jar")
        !warning.contains("a=1")
        !warning.contains("c=z")
        warning.contains(previewEnabled
            ? "; or add attributes to that variant so that it no longer matches."
            : "; add attributes to that variant so that it no longer matches; or enable the 'CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS' feature preview.")
        warning.contains("CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS") == !previewEnabled

        where:
        previewEnabled | expected
        false          | "secondary0"
        true           | "primary"
    }

    def "warning suggests no attributes when the variants selected without the primary variant are ambiguous (preview enabled: #previewEnabled)"() {
        given:
        def producer = producerOf([a: "1"], [[a: "1", f: "x", artifactType: "jar"], [a: "1", f: "y", artifactType: "jar"]])

        when:
        selector(previewEnabled).select(producer, AttributeTestUtil.attributes([a: "1"]), false)

        then:
        def warning = outputEventListener.events.find { it.logLevel == LogLevel.WARN }.toString()
        warning.contains("Request attributes that only the intended variant matches; ")
        !warning.contains("Request one of these attributes")

        where:
        previewEnabled << [false, true]
    }

    def "reports each warning once per selector, however many selections trigger it (preview enabled: #previewEnabled)"() {
        given:
        def producer = producerOf([a: "1"], [[a: "1", artifactType: "jar"]])

        when:
        def selector = selector(previewEnabled)
        2.times {
            selector.select(producer, AttributeTestUtil.attributes([a: "1"]), false)
        }

        then:
        outputEventListener.events.count { it.logLevel == LogLevel.WARN && it.toString().contains("variant 'primary'") } == 1

        where:
        previewEnabled << [false, true]
    }

    private ResolvedVariantSet producerOf(Map<String, String> primary, List<Map<String, String>> secondaries) {
        def candidates = [variant("primary", primary, true)] + secondaries.withIndex().collect { attributes, i -> variant("secondary${i}", attributes, false) }
        def transformer = Stub(ResolvedVariantTransformer) {
            transform(_, _, _) >> { ComponentIdentifier componentId, ResolvedVariant root, VariantDefinition definition -> artifacts("transformed " + root.asDescribable().displayName) }
        }
        new DefaultResolvedVariantSet(Stub(ComponentIdentifier), schema, ImmutableAttributes.EMPTY, ImmutableList.copyOf(candidates), transformer)
    }

    private String describe(ResolvedArtifactSet result) {
        if (result instanceof BrokenResolvedArtifactSet) {
            return "failure"
        }
        result == ResolvedArtifactSet.EMPTY ? "empty" : labels[result]
    }

    private static boolean failedWith(ResolvedArtifactSet result, Class<? extends Throwable> type) {
        result instanceof BrokenResolvedArtifactSet && Throwables.getCausalChain(result.failure).any { type.isInstance(it) }
    }

    private boolean warned() {
        // Either the deprecation for ignoring it, without the preview, or the warning for selecting it, with the preview
        outputEventListener.events.any { it.logLevel == LogLevel.WARN && it.toString() =~ /(ignored variant 'primary' because it|selected variant 'primary', which) has no artifacts/ }
    }

    private ResolvedVariant variant(String name, Map<String, String> attributes, boolean previouslyUnselectable) {
        def variantArtifacts = artifacts(name)
        Stub(ResolvedVariant) {
            getAttributes() >> AttributeTestUtil.attributes(attributes)
            isPreviouslyUnselectable() >> previouslyUnselectable
            getArtifacts() >> variantArtifacts
            asDescribable() >> Describables.of(name)
        }
    }

    private ResolvedArtifactSet artifacts(String label) {
        def artifacts = Stub(ResolvedArtifactSet)
        labels[artifacts] = label
        artifacts
    }

    private AttributeMatchingArtifactVariantSelector selector(boolean previewEnabled) {
        new AttributeMatchingArtifactVariantSelector(
            schema,
            transforms,
            AttributeTestUtil.attributesFactory(),
            AttributeTestUtil.services(),
            DependencyManagementTestUtil.newFailureHandler(),
            previewEnabled
        )
    }
}
