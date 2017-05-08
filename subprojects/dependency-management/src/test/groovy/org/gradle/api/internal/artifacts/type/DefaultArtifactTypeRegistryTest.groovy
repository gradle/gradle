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

package org.gradle.api.internal.artifacts.type

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantMetadata
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class DefaultArtifactTypeRegistryTest extends Specification {
    def attributesFactory = new DefaultImmutableAttributesFactory()
    def registry = new DefaultArtifactTypeRegistry(DirectInstantiator.INSTANCE, attributesFactory)

    def "creates as required and reuses"() {
        expect:
        def container = registry.create()
        container != null
        registry.create().is(container)
    }

    def "can attach an artifact type"() {
        expect:
        def container = registry.create()
        container.create('jar') {
            attributes.attribute(Attribute.of('thing', String), '123')
        }
        container['jar'].fileNameExtensions == ['jar'] as Set
        container['jar'].attributes.getAttribute(Attribute.of('thing', String)) == '123'
    }

    def "does not apply any mapping when no artifact types registered"() {
        def attrs = ImmutableAttributes.EMPTY
        def variant = Stub(VariantMetadata)

        given:
        variant.attributes >> attrs

        expect:
        registry.mapAttributesFor(variant) == attrs
    }

    def "does not apply any mapping when variant has no artifacts"() {
        def attrs = ImmutableAttributes.EMPTY
        def variant = Stub(VariantMetadata)

        given:
        variant.attributes >> attrs
        variant.artifacts >> []

        expect:
        registry.mapAttributesFor(variant) == attrs
    }

    def "adds artifactType attribute but does not apply any mapping when no matching artifact type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar"])
        def variant = Stub(VariantMetadata)
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        variant.attributes >> attrs
        variant.artifacts >> [artifact]
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("aar")

        expect:
        registry.mapAttributesFor(variant) == attrsPlusFormat
    }

    def "applies mapping when no attributes defined for matching type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar"])
        def variant = Stub(VariantMetadata)
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        variant.attributes >> attrs
        variant.artifacts >> [artifact]
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("jar")

        expect:
        registry.mapAttributesFor(variant) == attrsPlusFormat
    }

    def "applies mapping to matching artifact type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar", "custom": "123"])
        def variant = Stub(VariantMetadata)
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        variant.attributes >> attrs
        variant.artifacts >> [artifact]
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("jar").attributes.attribute(Attribute.of("custom", String), "123")

        expect:
        registry.mapAttributesFor(variant) == attrsPlusFormat
    }

    def "does not apply mapping when multiple artifacts with different types"() {
        def attrs = ImmutableAttributes.EMPTY
        def variant = Stub(VariantMetadata)
        def artifact1 = Stub(ComponentArtifactMetadata)
        def artifactName1 = Stub(IvyArtifactName)
        def artifact2 = Stub(ComponentArtifactMetadata)
        def artifactName2 = Stub(IvyArtifactName)

        given:
        variant.attributes >> attrs
        variant.artifacts >> [artifact1, artifact2]
        artifact1.name >> artifactName1
        artifactName1.extension >> "jar"
        artifactName1.type >> "jar"
        artifact2.name >> artifactName2
        artifactName2.extension >> "zip"
        artifactName2.type >> "zip"

        registry.create().create("jar").attributes.attribute(Attribute.of("custom", String), "123")
        registry.create().create("zip").attributes.attribute(Attribute.of("custom", String), "234")

        expect:
        registry.mapAttributesFor(variant) == attrs
    }

    def "does not apply mapping when variant already defines some attributes"() {
        def attrs = attributesFactory.of(Attribute.of("attr", String), "value")
        def variant = Stub(VariantMetadata)
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        variant.attributes >> attrs
        variant.artifacts >> [artifact]
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("jar").attributes.attribute(Attribute.of("custom", String), "123")

        expect:
        registry.mapAttributesFor(variant) == concat(attrs, ["artifactType": "jar"])
    }

    def concat(ImmutableAttributes source, Map<String, String> attrs) {
        def result = source
        attrs.each { key, value ->
            result = attributesFactory.concat(result, attributesFactory.of(Attribute.of(key, String), value))
        }
        return result
    }
}
