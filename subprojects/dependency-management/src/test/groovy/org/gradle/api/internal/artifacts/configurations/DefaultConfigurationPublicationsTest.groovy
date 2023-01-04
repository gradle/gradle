/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.DefaultFileSystemLocation
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.DefaultSetProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.SetProperty
import org.gradle.internal.Describables
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultConfigurationPublicationsTest extends Specification {
    def parentAttributes = ImmutableAttributes.EMPTY
    def artifacts = new DefaultPublishArtifactSet("artifacts", TestUtil.domainObjectCollectionFactory().newDomainObjectSet(PublishArtifact), TestFiles.fileCollectionFactory(), TestFiles.taskDependencyFactory())
    def allArtifacts = new DefaultPublishArtifactSet("artifacts", TestUtil.domainObjectCollectionFactory().newDomainObjectSet(PublishArtifact), TestFiles.fileCollectionFactory(), TestFiles.taskDependencyFactory())
    def artifactNotationParser = Stub(NotationParser)
    def capabilityNotationParser = Stub(NotationParser)
    def fileCollectionFactory = TestFiles.fileCollectionFactory()
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def displayName = Describables.of("<config>")
    def publications = new DefaultConfigurationPublications(displayName, artifacts, {
        allArtifacts
    }, parentAttributes, TestUtil.instantiatorFactory().decorateLenient(), artifactNotationParser, capabilityNotationParser, fileCollectionFactory, attributesFactory,
        TestUtil.domainObjectCollectionFactory(), TestFiles.taskDependencyFactory()
    )

    def setup() {
        artifacts.whenObjectAdded { allArtifacts.add(it) }
    }

    def "converts to OutgoingVariant when nothing defined"() {
        expect:
        def variant = publications.convertToOutgoingVariant()

        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 1

        def child = variant.children.first()
        child.asDescribable() == displayName
        child.attributes == publications.attributes
        child.artifacts == allArtifacts
        child.children.empty
    }

    def "converts to OutgoingVariant when artifacts declared"() {
        def artifact = Stub(PublishArtifact)

        given:
        publications.artifacts.add(artifact)

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 1

        def child = variant.children.first()
        child.asDescribable() == displayName
        child.attributes == publications.attributes
        child.artifacts == allArtifacts
        child.children.empty
    }

    def "converts to OutgoingVariant when artifacts inherited"() {
        def artifact = Stub(PublishArtifact)

        given:
        allArtifacts.add(artifact)

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 1

        def child = variant.children.first()
        child.asDescribable() == displayName
        child.attributes == publications.attributes
        child.artifacts == allArtifacts
        child.children.empty
    }

    def "converts to OutgoingVariant when attributes declared"() {
        given:
        publications.attributes.attribute(Attribute.of("thing", String), "value")

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 1

        def child = variant.children.first()
        child.asDescribable() == displayName
        child.attributes == publications.attributes
        child.artifacts == allArtifacts
        child.children.empty
    }

    def "converts to OutgoingVariant when explicit variant defined"() {
        def artifact = Stub(PublishArtifact)

        given:
        def variantDef = publications.variants.create("child")
        variantDef.attributes.attribute(Attribute.of("thing", String), "value")
        variantDef.artifacts.add(artifact)

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 1

        def child = variant.children.first()
        child.asDescribable().displayName == '<config> variant child'
        child.attributes == variantDef.attributes
        child.artifacts == variantDef.artifacts
        child.children.empty
    }

    def "converts to OutgoingVariant when explicit variant and artifacts defined"() {
        def artifact1 = Stub(PublishArtifact)
        def artifact2 = Stub(PublishArtifact)

        given:
        publications.artifacts.add(artifact1)
        publications.attributes.attribute(Attribute.of("thing", String), "value1")
        def variantDef = publications.variants.create("child")
        variantDef.attributes.attribute(Attribute.of("thing", String), "value2")
        variantDef.artifacts.add(artifact2)

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 2

        def implicit = variant.children.first()
        implicit.asDescribable() == displayName
        implicit.attributes == publications.attributes
        implicit.artifacts == allArtifacts
        implicit.children.empty

        def explicit = (variant.children as List)[1]
        explicit.asDescribable().displayName == '<config> variant child'
        explicit.attributes == variantDef.attributes
        explicit.artifacts == variantDef.artifacts
        explicit.children.empty
    }

    def "converts to OutgoingVariant when explicit variant and artifacts inherited"() {
        def artifact1 = Stub(PublishArtifact)
        def artifact2 = Stub(PublishArtifact)

        given:
        allArtifacts.add(artifact1)
        publications.attributes.attribute(Attribute.of("thing", String), "value1")
        def variantDef = publications.variants.create("child")
        variantDef.attributes.attribute(Attribute.of("thing", String), "value2")
        variantDef.artifacts.add(artifact2)

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.asDescribable() == displayName
        variant.attributes == publications.attributes
        variant.artifacts == publications.artifacts
        variant.children.size() == 2

        def implicit = variant.children.first()
        implicit.asDescribable() == displayName
        implicit.attributes == publications.attributes
        implicit.artifacts == allArtifacts
        implicit.children.empty

        def explicit = (variant.children as List)[1]
        explicit.asDescribable().displayName == '<config> variant child'
        explicit.attributes == variantDef.attributes
        explicit.artifacts == variantDef.artifacts
        explicit.children.empty
    }

    def "can declare outgoing artifacts using lazy provider for configuration"() {
        given:
        artifactNotationParser.parseNotation(_ as DefaultFileSystemLocation) >> { args ->
            def mockArtifact = Mock(ConfigurablePublishArtifact)
            mockArtifact.getName() >> ((DefaultFileSystemLocation) args[0]).getAsFile().getName()
            return mockArtifact
        }
        SetProperty<FileSystemLocation> prop = new DefaultSetProperty<>(Mock(PropertyHost), FileSystemLocation)

        when:
        publications.artifacts(prop)

        then:
        prop.get().isEmpty()

        when:
        def file1 = new DefaultFileSystemLocation(new File("file1"))
        prop.add(file1)

        then:
        prop.get().size() == 1
        publications.getArtifacts().toSet()*.getName() == ["file1"]

        when:
        def file2 = new DefaultFileSystemLocation(new File("file2"))
        prop.add(file2)

        then:
        prop.get().size() == 2
        publications.getArtifacts()*.name == ["file1"] // Added new file to prop, but artifacts already resolved
    }
}
