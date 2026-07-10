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
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class ArtifactTypeRegistryTest extends Specification {
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def registry = new ArtifactTypeRegistry(TestUtil.instantiatorFactory().decorateLenient(), attributesFactory, CollectionCallbackActionDecorator.NOOP)

    def "creates as required and reuses"() {
        expect:
        def container = registry.getArtifactTypeContainer()
        container != null
        registry.getArtifactTypeContainer().is(container)
    }

    def "can attach an artifact type"() {
        expect:
        def container = registry.getArtifactTypeContainer()
        container.create('jar') {
            attributes.attribute(Attribute.of('thing', String), '123')
        }
        container['jar'].fileNameExtensions == ['jar'] as Set
        container['jar'].attributes.getAttribute(Attribute.of('thing', String)) == '123'
    }

    def "does not apply any mapping when no artifact types registered"() {
        def attrs = ImmutableAttributes.EMPTY

        expect:
        toImmutable(registry).mapAttributesFor(attrs, []) == attrs
    }

    def "does not apply any mapping when variant has no artifacts"() {
        def attrs = ImmutableAttributes.EMPTY

        given:
        registry.getArtifactTypeContainer().create("aar")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, []) == attrs
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

        registry.getArtifactTypeContainer().create("aar")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
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

        registry.getArtifactTypeContainer().create("jar")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
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

        registry.getArtifactTypeContainer().create("jar").attributes.attribute(Attribute.of("custom", String), "123")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "adds only default attributes when multiple artifacts with different types"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["custom-default": "123"])
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

        registry.getArtifactTypeContainer().create("jar").attributes.attribute(Attribute.of("custom", String), "123")
        registry.getArtifactTypeContainer().create("zip").attributes.attribute(Attribute.of("custom", String), "234")
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom-default", String), "123")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact1, artifact2]) == attrsPlusFormat
    }

    def "maps only artifactType attribute for arbitrary files when no extensions are registered"() {
        expect:
        toImmutable(registry).mapAttributesFor(artifactFile).getAttribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE) == type

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
        registry.getArtifactTypeContainer().create(type).attributes.attribute(Attribute.of("custom", String), "123")

        expect:
        toImmutable(registry).mapAttributesFor(artifactFile) == attrsPlusFormat

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
        registry.getArtifactTypeContainer().create("baz").attributes.attribute(Attribute.of("custom", String), "123")
        registry.getArtifactTypeContainer().create("buzz").attributes.attribute(Attribute.of("custom", String), "234")

        expect:
        toImmutable(registry).mapAttributesFor(artifactFile) == attrsPlusFormat

        where:
        artifactFile    | type
        file("foo.jar") | ArtifactTypeDefinition.JAR_TYPE
        file("foo.zip") | ArtifactTypeDefinition.ZIP_TYPE
        file("foo.bar") | "bar"
        file("foo")     | ""
        dir("foo")      | ArtifactTypeDefinition.DIRECTORY_TYPE
        dir("foo.jar")  | ArtifactTypeDefinition.DIRECTORY_TYPE
    }

    def "applies default attributes even when no artifact types registered"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["custom-default": "123"])

        when:
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom-default", String), "123")

        then:
        toImmutable(registry).mapAttributesFor(attrs, []) == attrsPlusFormat
    }

    def "applies mapping and default attributes to matching artifact type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar", "custom": "123", "custom-default": "123"])
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.getArtifactTypeContainer().create("jar").attributes.attribute(Attribute.of("custom", String), "123")
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom-default", String), "123")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "applies default attributes when no attributes defined for matching type"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar", "custom-default": "123"])
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.getArtifactTypeContainer().create("jar")
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom-default", String), "123")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "maps default attributes for arbitrary files"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": type, "custom-default": "123"])

        given:
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom-default", String), "123")

        expect:
        toImmutable(registry).mapAttributesFor(artifactFile) == attrsPlusFormat

        where:
        artifactFile    | type
        file("foo.jar") | ArtifactTypeDefinition.JAR_TYPE
        file("foo.zip") | ArtifactTypeDefinition.ZIP_TYPE
        file("foo.bar") | "bar"
        dir("dir")      | ArtifactTypeDefinition.DIRECTORY_TYPE
    }

    def "registered attributes win over default attributes for artifacts"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": "jar", "custom": "123"])
        def artifact = Stub(ComponentArtifactMetadata)
        def artifactName = Stub(IvyArtifactName)

        given:
        artifact.name >> artifactName
        artifactName.extension >> "jar"
        artifactName.type >> "jar"

        registry.getArtifactTypeContainer().create("jar").attributes.attribute(Attribute.of("custom", String), "123")
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom", String), "321")

        expect:
        toImmutable(registry).mapAttributesFor(attrs, [artifact]) == attrsPlusFormat
    }

    def "registered attributes win over default attributes for file"() {
        def attrs = ImmutableAttributes.EMPTY
        def attrsPlusFormat = concat(attrs, ["artifactType": ArtifactTypeDefinition.JAR_TYPE, "custom": "123"])

        given:
        registry.getArtifactTypeContainer().create(ArtifactTypeDefinition.JAR_TYPE).attributes.attribute(Attribute.of("custom", String), "123")
        registry.defaultArtifactAttributes.attribute(Attribute.of("custom", String), "321")

        expect:
        toImmutable(registry).mapAttributesFor(file("foo.jar")) == attrsPlusFormat
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

    static ImmutableArtifactTypeRegistry toImmutable(ArtifactTypeRegistry registry) {
        AttributeTestUtil.services().artifactTypeRegistryFactory.create(registry)
    }
}
