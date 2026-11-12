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

import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.Describables
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
class ArtifactlessPrimaryVariantSelectionTest extends Specification {
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
        def primaryVariant = variant("primary", primary, true)
        def candidates = [primaryVariant] + secondaries.withIndex().collect { attributes, i -> variant("secondary${i}", attributes, false) }
        def producer = Stub(ResolvedVariantSet) {
            getProducerSchema() >> schema
            getOverriddenAttributes() >> ImmutableAttributes.EMPTY
            getCandidates() >> candidates
            asDescribable() >> Describables.of("<component>")
            transformCandidate(_, _) >> { ResolvedVariant root, VariantDefinition definition -> artifacts("transformed " + root.asDescribable().displayName) }
        }
        transforms.findCandidateTransformationChains(_, _) >> { List<ResolvedVariant> sources, ImmutableAttributes target ->
            sources.findAll { it.asDescribable().displayName in transformable }.collect { new TransformedVariant(it, Stub(VariantDefinition) { getTargetAttributes() >> target }) }
        }

        when:
        def result = selector(previewEnabled).select(producer, AttributeTestUtil.attributes(requested), false)

        then:
        (result instanceof BrokenResolvedArtifactSet ? "failure" : labels[result]) == expected
        outputEventListener.events.any { it.logLevel == LogLevel.WARN && it.toString().contains("ignored variant 'primary' because it has no artifacts") } == warns

        where:
        primary          | secondaries                          | requested                     | transformable  | previewEnabled | expected                 | warns
        // An equal or extra-attribute tie goes to the variant with artifacts
        [a: "1"]         | [[a: "1"]]                           | [a: "1"]                      | []             | false          | "secondary0"             | false
        [a: "1"]         | [[a: "1"]]                           | [a: "1"]                      | []             | true           | "secondary0"             | false
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1"]                      | []             | false          | "secondary0"             | false
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1"]                      | []             | true           | "secondary0"             | false
        // A strictly better match for the requested attributes
        [a: "1", b: "x"] | [[a: "1"]]                           | [a: "1", b: "x"]              | []             | false          | "secondary0"             | true
        [a: "1", b: "x"] | [[a: "1"]]                           | [a: "1", b: "x"]              | []             | true           | "primary"                | false
        // Nothing else matches
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | []             | false          | "failure"                | true
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | []             | true           | "primary"                | false
        // A transformation beats a primary variant that lacks a requested attribute
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1", artifactType: "txt"] | ["secondary0"] | false          | "transformed secondary0" | false
        [a: "1"]         | [[a: "1", artifactType: "jar"]]      | [a: "1", artifactType: "txt"] | ["secondary0"] | true           | "transformed secondary0" | false
        // A primary variant that declares every requested attribute beats a transformation
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | ["secondary0"] | false          | "transformed secondary0" | true
        [a: "1"]         | [[a: "2"]]                           | [a: "1"]                      | ["secondary0"] | true           | "primary"                | false
        // Only a transformation of the primary variant matches
        [a: "1"]         | [[a: "2"]]                           | [a: "3"]                      | ["primary"]    | false          | "failure"                | true
        [a: "1"]         | [[a: "2"]]                           | [a: "3"]                      | ["primary"]    | true           | "transformed primary"    | false
        // An incompatible primary variant does not matter
        [a: "1"]         | [[a: "2"]]                           | [a: "2"]                      | []             | false          | "secondary0"             | false
        [a: "1"]         | [[a: "2"]]                           | [a: "2"]                      | []             | true           | "secondary0"             | false
        // Secondary variants that tie stay ambiguous
        [a: "1"]         | [[a: "1", f: "x"], [a: "1", f: "y"]] | [a: "1"]                      | []             | false          | "failure"                | false
        [a: "1"]         | [[a: "1", f: "x"], [a: "1", f: "y"]] | [a: "1"]                      | []             | true           | "failure"                | false
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
