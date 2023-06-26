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
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Specification

class ResolvedVariantArtifactSetTest extends Specification {
    def componentId = Stub(ComponentIdentifier)
    def selector = Mock(VariantSelector)

    def "returns empty set when component id does not match spec"() {
        given:
        def allVariants = Stub(ResolvedVariantSet)
        def legacyVariants = Stub(ResolvedVariantSet)

        when:
        def selected = new ResolvedVariantArtifactSet(componentId, () -> allVariants, legacyVariants).select({ false }, selector, selectFromAll)

        then:
        0 * selector.select(_, _)
        selected == ResolvedArtifactSet.EMPTY

        where:
        selectFromAll << [true, false]
    }

    def "does not access all artifacts when selecting one variant"() {
        def ownerId = DefaultModuleVersionIdentifier.newId("group", "name", "1.0")

        def variant1 = makeVariantNamed("variant1", ownerId)
        def variant2 = makeVariantNamed("variant2", ownerId)

        def variants = Stub(ResolvedVariantSet) {
            getVariants() >> ([variant1, variant2] as Set)
        }

        when:
        def artifactSet = new ResolvedVariantArtifactSet(componentId, () -> variants, variants)
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
        }, selectFromAll)

        then:
        0 * _

        where:
        selectFromAll << [true, false]
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
        given:
        def allVariants = Stub(ResolvedVariantSet)
        def legacyVariants = Stub(ResolvedVariantSet)
        def artifacts = Stub(ResolvedArtifactSet)

        when:
        def selected = new ResolvedVariantArtifactSet(componentId, () -> allVariants, legacyVariants).select({ true }, selector, selectFromAll)

        then:
        1 * selector.select(selectFromAll ? allVariants : legacyVariants, _) >> artifacts
        selected == artifacts

        where:
        selectFromAll << [true, false]
    }
}
