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

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.typeconversion.NotationParser
import spock.lang.Specification

class DefaultConfigurationPublicationsTest extends Specification {
    def parentAttributes = Stub(AttributeContainerInternal)
    def artifacts = Stub(PublishArtifactSet)
    def notationParser = Stub(NotationParser)
    def fileCollectionFactory = Stub(FileCollectionFactory)
    def attributesFactory = new DefaultImmutableAttributesFactory()
    def publications = new DefaultConfigurationPublications(artifacts, parentAttributes, DirectInstantiator.INSTANCE, notationParser, fileCollectionFactory, attributesFactory)

    def "converts to OutgoingVariant when no variants defined"() {
        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.attributes == parentAttributes
        variant.artifacts == artifacts
        variant.children.empty
    }

    def "converts to OutgoingVariant when variants defined"() {
        given:
        def variantDef = publications.variants.create("child")
        variantDef.getAttributes().attribute(Attribute.of("thing", String.class), "value")
        variantDef.artifacts.add(Stub(PublishArtifact))

        expect:
        def variant = publications.convertToOutgoingVariant()
        variant.attributes == parentAttributes
        variant.artifacts == artifacts
        variant.children.size() == 1

        def child = variant.children.first()
        child.attributes == variantDef.attributes
        child.artifacts == variantDef.artifacts
        child.children.empty
    }
}
