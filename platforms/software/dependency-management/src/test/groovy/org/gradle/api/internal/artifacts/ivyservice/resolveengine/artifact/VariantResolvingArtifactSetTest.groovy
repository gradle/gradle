/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.GraphVariantSelectionResult
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.VariantArtifactResolveState
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.resolve.resolver.VariantArtifactResolver
import org.gradle.util.TestUtil
import spock.lang.Specification

class VariantResolvingArtifactSetTest extends Specification {

    VariantArtifactResolver variantResolver
    ComponentGraphResolveState component
    VariantGraphResolveState variant
    DependencyGraphEdge dependency
    GraphVariantSelector graphSelector
    AttributesSchemaInternal consumerSchema
    CalculatedValueContainerFactory calculatedValueContainerFactory

    def selector = Mock(ArtifactVariantSelector)

    def setup() {
        variantResolver = Mock(VariantArtifactResolver)
        component = Stub(ComponentGraphResolveState) {
            getMetadata() >> Mock(ComponentGraphResolveMetadata)
        }
        variant = Mock(VariantGraphResolveState)
        dependency = Mock(DependencyGraphEdge) {
            getDependencyMetadata() >> Mock(DependencyMetadata) {
                getArtifacts() >> []
            }
            getExclusions() >> Mock(ExcludeSpec)
            getAttributes() >> ImmutableAttributes.EMPTY
            getSelector() >> Mock(DependencyGraphSelector) {
                getRequested() >> Mock(ComponentSelector) {
                    getRequestedCapabilities() >> []
                }
            }
        }
        graphSelector = Mock(GraphVariantSelector) {
            selectVariantsLenient(_, _, _, _, _) >> Mock(GraphVariantSelectionResult) {
                getVariants() >> [variant]
            }
        }
        consumerSchema = Mock(AttributesSchemaInternal)
        calculatedValueContainerFactory = TestUtil.calculatedValueContainerFactory()
    }

    def "returns empty set when component id does not match spec"() {
        when:
        def artifactSet = new VariantResolvingArtifactSet(variantResolver, component, variant, dependency, graphSelector, consumerSchema, calculatedValueContainerFactory)
        def spec = new ArtifactSelectionSpec(ImmutableAttributes.EMPTY, { false }, selectFromAll, false, ResolutionStrategy.SortOrder.DEFAULT)
        def selected = artifactSet.select(selector, spec)

        then:
        0 * selector.select(_, _, _, _)
        selected == ResolvedArtifactSet.EMPTY

        where:
        selectFromAll << [true, false]
    }

    def "does not access all artifacts when selecting one variant"() {
        def subvariant1 = Mock(VariantResolveMetadata)
        def subvariant2 = Mock(VariantResolveMetadata)

        variant.prepareForArtifactResolution() >> Mock(VariantArtifactResolveState) {
            getArtifactVariants() >> ([subvariant1, subvariant2] as Set)
        }

        when:
        def spec = new ArtifactSelectionSpec(ImmutableAttributes.EMPTY, { true }, false, false, ResolutionStrategy.SortOrder.DEFAULT)
        def artifactSet = new VariantResolvingArtifactSet(variantResolver, component, variant, dependency, graphSelector, consumerSchema, calculatedValueContainerFactory)
        artifactSet.select(new ArtifactVariantSelector() {
            @Override
            ResolvedArtifactSet select(ResolvedVariantSet candidates, ImmutableAttributes requestAttributes, boolean allowNoMatchingVariants, ArtifactVariantSelector.ResolvedArtifactTransformer factory) {
                assert candidates.variants.size() == 2
                // select the first variant
                return candidates.variants[0].artifacts
            }
        }, spec)

        then:
        1 * variantResolver.resolveVariant(_, subvariant1) >> Mock(ResolvedVariant)
        1 * variantResolver.resolveVariant(_, subvariant2) >> Mock(ResolvedVariant)
        0 * variantResolver._
    }

    def "selects artifacts when component id matches spec"() {
        given:
        def subvariant1 = Mock(VariantResolveMetadata)
        def subvariant2 = Mock(VariantResolveMetadata)

        variant.prepareForArtifactResolution() >> Mock(VariantArtifactResolveState) {
            getArtifactVariants() >> ([subvariant1, subvariant2] as Set)
        }

        def artifacts = Stub(ResolvedArtifactSet)
        def artifactSet = new VariantResolvingArtifactSet(variantResolver, component, variant, dependency, graphSelector, consumerSchema, calculatedValueContainerFactory)

        when:
        def spec = new ArtifactSelectionSpec(ImmutableAttributes.EMPTY, { true }, selectFromAll, false, ResolutionStrategy.SortOrder.DEFAULT)
        def selected = artifactSet.select(selector, spec)

        then:
        1 * selector.select(_, _, _, _) >> artifacts
        _ * variantResolver.resolveVariant(_, _) >> Mock(ResolvedVariant)
        selected == artifacts

        where:
        selectFromAll << [true, false]
    }
}
