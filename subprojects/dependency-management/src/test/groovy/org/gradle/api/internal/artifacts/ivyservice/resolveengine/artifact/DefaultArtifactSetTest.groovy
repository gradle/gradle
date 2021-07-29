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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
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
