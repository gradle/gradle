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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorBuilder
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultMutableIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.DefaultMutableMavenModuleResolveMetadata
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.util.TestUtil
import spock.lang.Specification

class ComponentMetadataDetailsAdapterTest extends Specification {
    private instantiator = DirectInstantiator.INSTANCE
    private notationParser = DependencyMetadataNotationParser.parser(instantiator)

    def versionIdentifier = new DefaultModuleVersionIdentifier("org.test", "producer", "1.0")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(versionIdentifier)
    def attributes = TestUtil.attributesFactory().of(Attribute.of("someAttribute", String), "someValue")
    def variantDefinedInGradleMetadata

    def adapterOnMavenMetadata = new ComponentMetadataDetailsAdapter(new DefaultMutableMavenModuleResolveMetadata(versionIdentifier, componentIdentifier), instantiator, notationParser)
    def adapterOnIvyMetadata = new ComponentMetadataDetailsAdapter(ivyComponentMetadata(), instantiator, notationParser)
    def adapterOnGradleMetadata = new ComponentMetadataDetailsAdapter(gradleComponentMetadata(), instantiator, notationParser)

    private ivyComponentMetadata() {
        new DefaultMutableIvyModuleResolveMetadata(versionIdentifier, componentIdentifier, [new Configuration("configurationDefinedInIvyMetadata", true, true, [])], [], [], [])
    }
    private gradleComponentMetadata() {
        def metadata = new DefaultMutableMavenModuleResolveMetadata(versionIdentifier, componentIdentifier)
        variantDefinedInGradleMetadata = metadata.addVariant("variantDefinedInGradleMetadata", attributes) //gradle metadata is distinguished from maven POM metadata by explicitly defining variants
        metadata
    }

    def "sees variants defined in Gradle metadata"() {
        given:
        def rule = Mock(Action)

        when:
        adapterOnGradleMetadata.withVariant("variantDefinedInGradleMetadata", rule)

        then:
        noExceptionThrown()
        1 * rule.execute(_)
    }

    def "treats ivy configurations as variants"() {
        given:
        def rule = Mock(Action)

        when:
        adapterOnIvyMetadata.withVariant("configurationDefinedInIvyMetadata", rule)

        then:
        noExceptionThrown()
        1 * rule.execute(_)
    }

    def "treats maven scopes as variants"() {
        given:
        //historically, we defined default MAVEN2_CONFIGURATIONS which eventually should become MAVEN2_VARIANTS
        def mavenVariants = GradlePomModuleDescriptorBuilder.MAVEN2_CONFIGURATIONS.keySet()
        def variantCount = mavenVariants.size()
        def rule = Mock(Action)

        when:
        mavenVariants.each {
            adapterOnMavenMetadata.withVariant(it, rule)
        }

        then:
        noExceptionThrown()
        variantCount * rule.execute(_)
    }

    def "fails when selecting a variant that does not exist"() {
        when:
        adapterOnGradleMetadata.withVariant("doesNotExist", {})

        then:
        def e = thrown(GradleException)
        e.message == "Variant doesNotExist is not declared for org.test:producer:1.0"
    }

    def "fails when selecting a maven scope that does not exist"() {
        when:
        adapterOnMavenMetadata.withVariant("doesNotExist", {})

        then:
        def e = thrown(GradleException)
        e.message == "Variant doesNotExist is not declared for org.test:producer:1.0"
    }

    def "fails when selecting an ivy configuration that does not exist"() {
        when:
        adapterOnIvyMetadata.withVariant("doesNotExist", {})

        then:
        def e = thrown(GradleException)
        e.message == "Variant doesNotExist is not declared for org.test:producer:1.0"
    }
}
