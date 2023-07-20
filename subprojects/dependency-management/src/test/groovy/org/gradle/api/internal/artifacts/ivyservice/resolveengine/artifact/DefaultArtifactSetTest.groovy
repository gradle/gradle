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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Specification

class DefaultArtifactSetTest extends Specification {
    def componentId = Stub(ComponentIdentifier)
    def schema = Stub(AttributesSchemaInternal)
    def artifactTypeRegistry = Stub(ArtifactTypeRegistry)

    def setup() {
        artifactTypeRegistry.mapAttributesFor(_) >> ImmutableAttributes.EMPTY
    }

    def "returns empty set when component id does not match spec"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def variant3 = Stub(ResolvedVariant)

        given:
        def artifacts1 = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant1, variant2] as Set), [variant1, variant2] as Set)
        def artifacts2 = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant1] as Set), [variant1] as Set)
        def artifacts3 = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant3] as Set), [variant3] as Set)

        expect:
        artifacts1.select({ false }, Stub(VariantSelector)) == ResolvedArtifactSet.EMPTY
        artifacts2.select({ false }, Stub(VariantSelector)) == ResolvedArtifactSet.EMPTY
        artifacts3.select({ false }, Stub(VariantSelector)) == ResolvedArtifactSet.EMPTY
    }

    def "does not access all artifacts when selecting one variant"() {
        def ownerId = DefaultModuleVersionIdentifier.newId("group", "name", "1.0")

        def variant1 = makeVariantNamed("variant1", ownerId)
        def variant2 = makeVariantNamed("variant2", ownerId)

        when:
        def artifactSet = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant1, variant2] as Set), [variant1, variant2] as Set)
        artifactSet.select({ true }, new VariantSelector() {
            @Override
            ResolvedArtifactSet select(ResolvedVariantSet candidates, VariantSelector.Factory factory) {
                assert candidates.variants.size() == 2
                // select the first variant
                return candidates.variants[0].artifacts
            }

            @Override
            ImmutableAttributes getRequestedAttributes() {
                return ImmutableAttributes.EMPTY
            }
        })

        then:
        0 * _
    }

    private static ResolvedVariant makeVariantNamed(String name, ModuleVersionIdentifier ownerId) {
        def id = DefaultModuleComponentIdentifier.newId(ownerId)
        def capabilities = ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(ownerId))
        def ivyArtifactName = new DefaultIvyArtifactName(name, "jar", "jar")
        def artifact = new DefaultResolvableArtifact(ownerId, ivyArtifactName, new DefaultModuleComponentArtifactIdentifier(id, ivyArtifactName), null, null, null)
        def artifacts = ImmutableList.of(artifact)
        return new ArtifactBackedResolvedVariant(null, Describables.of(name), ImmutableAttributes.EMPTY, capabilities, [], { artifacts })
    }

    def "selects artifacts when component id matches spec"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def variant3 = Stub(ResolvedVariant)
        def resolvedVariant1 = Stub(ResolvedArtifactSet)
        def selector = Stub(VariantSelector)

        given:
        def artifacts1 = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant1, variant2] as Set), [variant1, variant2] as Set)
        def artifacts2 = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant1] as Set), [variant1] as Set)
        def artifacts3 = new DefaultArtifactSet(componentId, schema, ImmutableAttributes.EMPTY, () -> ([variant3] as Set), [variant3] as Set)

        selector.select(_, _) >> resolvedVariant1

        expect:
        artifacts1.select({ true }, selector) == resolvedVariant1
        artifacts2.select({ true }, selector) == resolvedVariant1
        artifacts3.select({ true }, selector) == resolvedVariant1
    }
}
