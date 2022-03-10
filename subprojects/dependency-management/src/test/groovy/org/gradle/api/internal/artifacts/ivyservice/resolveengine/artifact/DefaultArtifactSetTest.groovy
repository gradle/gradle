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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ImmutableCapability
import org.gradle.internal.component.external.model.ModuleComponentOptionalArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DefaultVariantMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import spock.lang.Specification

class DefaultArtifactSetTest extends Specification {
    def componentId = Stub(ComponentIdentifier)
    def schema = Stub(AttributesSchemaInternal)
    def artifactTypeRegistry = Stub(ArtifactTypeRegistry)
    def calculatedValueContainerFactory = Stub(CalculatedValueContainerFactory)

    def setup() {
        artifactTypeRegistry.mapAttributesFor(_) >> ImmutableAttributes.EMPTY
    }

    def "returns empty set when component id does not match spec"() {
        def variant1 = Stub(VariantResolveMetadata)
        def variant2 = Stub(VariantResolveMetadata)
        def ownerId = Stub(ModuleVersionIdentifier)

        given:
        def artifacts1 = DefaultArtifactSet.createFromVariantMetadata(componentId, ownerId, null, null, [variant1, variant2] as Set, schema, null, null, artifactTypeRegistry, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)
        def artifacts2 = DefaultArtifactSet.createFromVariantMetadata(componentId, ownerId, null, null, [variant1] as Set, schema, null, null, artifactTypeRegistry, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)
        def artifacts3 = DefaultArtifactSet.adHocVariant(componentId, ownerId, [] as Set, null, null, schema, null, null, artifactTypeRegistry, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)

        ownerId.group >> "group"
        ownerId.name >> "name"
        ownerId.version >> "1.0"

        expect:
        artifacts1.select({ false }, Stub(VariantSelector)) == ResolvedArtifactSet.EMPTY
        artifacts2.select({ false }, Stub(VariantSelector)) == ResolvedArtifactSet.EMPTY
        artifacts3.select({ false }, Stub(VariantSelector)) == ResolvedArtifactSet.EMPTY
    }

    def "does not access all artifacts when selecting one variant"() {
        def ownerId = DefaultModuleVersionIdentifier.newId("group", "name", "1.0")

        def variant1 = makeVariantNamed("variant1", ownerId)
        def variant2 = makeVariantNamed("variant2", ownerId)

        def artifactResolver = Mock(ArtifactResolver)

        def moduleSources = Mock(ModuleSources)
        def exclusions = new DefaultExcludeFactory().nothing()

        when:
        def artifactSet = DefaultArtifactSet.createFromVariantMetadata(componentId, ownerId, moduleSources, exclusions, [variant1, variant2] as Set, schema, artifactResolver, new HashMap<ComponentArtifactIdentifier, ResolvableArtifact>(), artifactTypeRegistry, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)
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

        then: 'artifactResolver.resolveArtifact should only be invoked once by DefaultArtifactSet#createFromVariantMetadata and ArtifactSet#select'
        1 * artifactResolver.resolveArtifact(variant1.artifacts[0], moduleSources, _) >> { args ->
            ComponentArtifactMetadata artifact = args[0]
            BuildableArtifactResolveResult result = args[2]
            result.notFound(artifact.getId())
        }
        0 * _
    }

    private static DefaultVariantMetadata makeVariantNamed(String name, ModuleVersionIdentifier ownerId) {
        def id = DefaultModuleComponentIdentifier.newId(ownerId)
        def capabilities = ImmutableCapabilities.of(ImmutableCapability.defaultCapabilityForComponent(ownerId))
        def artifact = new ModuleComponentOptionalArtifactMetadata(id, new DefaultIvyArtifactName(name, "jar", "jar"))
        def artifacts = ImmutableList.of(artifact)
        return new DefaultVariantMetadata(name, null, Describables.of(name), ImmutableAttributes.EMPTY, artifacts, capabilities)
    }

    def "selects artifacts when component id matches spec"() {
        def variant1 = Stub(VariantResolveMetadata)
        def variant2 = Stub(VariantResolveMetadata)
        def resolvedVariant1 = Stub(ResolvedArtifactSet)
        def selector = Stub(VariantSelector)
        def ownerId = Stub(ModuleVersionIdentifier)

        given:
        def artifacts1 = DefaultArtifactSet.createFromVariantMetadata(componentId, ownerId, null, null, [variant1, variant2] as Set, schema, null, null, artifactTypeRegistry, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)
        def artifacts2 = DefaultArtifactSet.createFromVariantMetadata(componentId, ownerId, null, null, [variant1] as Set, schema, null, null, artifactTypeRegistry, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)
        def artifacts3 = DefaultArtifactSet.adHocVariant(componentId, ownerId, [] as Set, null, null, schema, null, null, artifactTypeRegistry, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, calculatedValueContainerFactory)

        selector.select(_, _) >> resolvedVariant1
        ownerId.group >> "group"
        ownerId.name >> "name"
        ownerId.version >> "1.0"

        expect:
        artifacts1.select({ true }, selector) == resolvedVariant1
        artifacts2.select({ true }, selector) == resolvedVariant1
        artifacts3.select({ true }, selector) == resolvedVariant1
    }
}
