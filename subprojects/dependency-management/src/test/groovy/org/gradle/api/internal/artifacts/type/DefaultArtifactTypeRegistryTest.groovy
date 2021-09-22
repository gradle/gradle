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


import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultArtifactTypeRegistryTest extends Specification {
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def registry = new DefaultArtifactTypeRegistry(TestUtil.instantiatorFactory().decorateLenient(), attributesFactory, CollectionCallbackActionDecorator.NOOP, Stub(VariantTransformRegistry))

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

        expect:
        registry.mapAttributesFor(attrs, []) == attrs
    }

    def "does not apply any mapping when variant has no artifacts"() {
        def attrs = ImmutableAttributes.EMPTY

        given:
        registry.create().create("aar")

        expect:
        registry.mapAttributesFor(attrs, []) == attrs
    }

    def "adds artifactType attribute but does not apply any mapping when no matching artifact type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar"])
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("aar")

        expect:
        registry.mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "applies mapping when no attributes defined for matching type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar"])
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("jar")

        expect:
        registry.mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "applies mapping to matching artifact type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar", "custom": "123"])
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.create().create("jar").attributes.attribute(Attribute.of("custom", String), "123")

        expect:
        registry.mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "does not apply mapping when multiple artifacts with different types"() {
        def attrs = ImmutableAttributes.EMPTY
        def artifact1 = Stub(ComponentArtifactMetadata)
        def artifactName1 = Stub(IvyArtifactName)
        def artifact2 = Stub(ComponentArtifactMetadata)
        def artifactName2 = Stub(IvyArtifactName)

        given:
        artifact1.name >> artifactName1
        artifactName1.extension >> "jar"
        artifactName1.type >> "jar"
        artifact2.name >> artifactName2
        artifactName2.extension >> "zip"
        artifactName2.type >> "zip"

        registry.create().create("jar").attributes.attribute(Attribute.of("custom", String), "123")
        registry.create().create("zip").attributes.attribute(Attribute.of("custom", String), "234")

        expect:
        registry.mapAttributesFor(attrs, [artifact1, artifact2]) == attrs
    }

    def "maps only artifactType attribute for arbitrary files when no extensions are registered"() {
        expect:
        registry.mapAttributesFor(artifactFile).getAttribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE) == type

        where:
        artifactFile    | type
        file("foo.jar") | ArtifactTypeDefinition.JAR_TYPE
        file("foo.zip") | ArtifactTypeDefinition.ZIP_TYPE
        file("foo.bar") | "bar"
        file("foo")     | ""
        dir("foo")      | ArtifactTypeDefinition.DIRECTORY_TYPE
        dir("foo.jar")  | ArtifactTypeDefinition.DIRECTORY_TYPE
    }

    def "maps all attributes for arbitrary files when matching extensions are registered"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": type, "custom": "123"])

        given:
        registry.create().create(type).attributes.attribute(Attribute.of("custom", String), "123")

        expect:
        registry.mapAttributesFor(artifactFile) == attrsPlusFormat

        where:
        artifactFile    | type
        file("foo.jar") | ArtifactTypeDefinition.JAR_TYPE
        file("foo.zip") | ArtifactTypeDefinition.ZIP_TYPE
        file("foo.bar") | "bar"
    }

    def "maps only artifactType attribute for arbitrary files when extensions are registered but none match"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": type])

        given:
        registry.create().create("baz").attributes.attribute(Attribute.of("custom", String), "123")
        registry.create().create("buzz").attributes.attribute(Attribute.of("custom", String), "234")

        expect:
        registry.mapAttributesFor(artifactFile) == attrsPlusFormat

        where:
        artifactFile    | type
        file("foo.jar") | ArtifactTypeDefinition.JAR_TYPE
        file("foo.zip") | ArtifactTypeDefinition.ZIP_TYPE
        file("foo.bar") | "bar"
        file("foo")     | ""
        dir("foo")      | ArtifactTypeDefinition.DIRECTORY_TYPE
        dir("foo.jar")  | ArtifactTypeDefinition.DIRECTORY_TYPE
    }

    File file(String name) {
        return Stub(File) {
            getName() >> name
            isDirectory() >> false
            isFile() >> true
        }
    }

    File dir(String name) {
        return Stub(File) {
            getName() >> name
            isDirectory() >> true
            isFile() >> false
        }
    }

    def concat(ImmutableAttributes source, Map<String, String> attrs) {
        def result = source
        attrs.each { key, value ->
            result = attributesFactory.concat(result, attributesFactory.of(Attribute.of(key, String), value))
        }
        return result
    }
}
