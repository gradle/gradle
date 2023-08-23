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
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorBuilder
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.api.problems.UsesTestProblems
import org.gradle.internal.component.VariantSelectionFailureProcessor
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.AttributeMatchingConfigurationSelector
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class ComponentMetadataDetailsAdapterTest extends Specification implements UsesTestProblems {
    private instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private dependencyMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DirectDependencyMetadataImpl.class, SimpleMapInterner.notThreadSafe())
    private dependencyConstraintMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DependencyConstraintMetadataImpl.class, SimpleMapInterner.notThreadSafe())
    private componentIdentifierNotationParser = new ComponentIdentifierParserFactory().create()

    def versionIdentifier = DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId("org.test", "producer"), "1.0")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(versionIdentifier)
    def testAttribute = Attribute.of("someAttribute", String)
    def attributes = AttributeTestUtil.attributesFactory().of(testAttribute, "someValue")
    def schema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
    def ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    def mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()

    def gradleMetadata
    def adapterOnMavenMetadata = new ComponentMetadataDetailsAdapter(mavenComponentMetadata(), instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, DependencyManagementTestUtil.platformSupport())
    def adapterOnIvyMetadata = new ComponentMetadataDetailsAdapter(ivyComponentMetadata(), instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, DependencyManagementTestUtil.platformSupport())
    def adapterOnGradleMetadata = new ComponentMetadataDetailsAdapter(gradleComponentMetadata(), instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, DependencyManagementTestUtil.platformSupport())

    private ivyComponentMetadata() {
        ivyMetadataFactory.create(componentIdentifier, [], [new Configuration("configurationDefinedInIvyMetadata", true, true, [])], [], [])
    }
    private gradleComponentMetadata() {
        def metadata = mavenMetadataFactory.create(componentIdentifier, [])
        metadata.addVariant("variantDefinedInGradleMetadata1", attributes) //gradle metadata is distinguished from maven POM metadata by explicitly defining variants
        metadata.addVariant("variantDefinedInGradleMetadata2", AttributeTestUtil.attributesFactory().of(testAttribute, "other")) //gradle metadata is distinguished from maven POM metadata by explicitly defining variants
        gradleMetadata = metadata
        metadata
    }

    private MutableMavenModuleResolveMetadata mavenComponentMetadata() {
        mavenMetadataFactory.create(componentIdentifier, [])
    }

    def setup() {
        schema.attribute(testAttribute)
    }

    def "sees variants defined in Gradle metadata"() {
        given:
        def rule = Mock(Action)

        when:
        adapterOnGradleMetadata.withVariant("variantDefinedInGradleMetadata1", rule)

        then:
        noExceptionThrown()
        1 * rule.execute(_)
    }

    def "can execute rule on all variants"() {
        given:
        def adapterRule = Mock(Action)
        def dependenciesRule = Mock(Action)
        def constraintsRule = Mock(Action)
        def attributesRule = Mock(Action)
        when:
        adapterOnGradleMetadata.allVariants(adapterRule)

        then: "the adapter rule is called once"
        noExceptionThrown()
        1 * adapterRule.execute(_) >> {
            def adapter = it[0]
            adapter.withDependencies(dependenciesRule)
            adapter.withDependencyConstraints(constraintsRule)
            adapter.attributes(attributesRule)
        }
        0 * _

        when:
        resolve(gradleMetadata)

        then: "attributes are used during matching, the rule is applied on all variants"
        2 * attributesRule.execute(_)

        and: " we only apply the dependencies rule to the selected variant"
        1 * dependenciesRule.execute(_)
        1 * constraintsRule.execute(_)
        0 * _
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

    void resolve(MutableModuleComponentResolveMetadata component) {
        def immutable = component.asImmutable()
        def componentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.test", "consumer"), "1.0")
        def consumerIdentifier = DefaultModuleVersionIdentifier.newId(componentIdentifier)
        def componentSelector = newSelector(DefaultModuleIdentifier.newId(consumerIdentifier.group, consumerIdentifier.name), new DefaultMutableVersionConstraint(consumerIdentifier.version))
        def consumer = new LocalComponentDependencyMetadata(componentSelector, ImmutableAttributes.EMPTY, null, [] as List, [], false, false, true, false, false, null)
        def state = DependencyManagementTestUtil.modelGraphResolveFactory().stateFor(immutable)
        def configurationSelector = new AttributeMatchingConfigurationSelector(new VariantSelectionFailureProcessor(createTestProblems()))

        def variant = consumer.selectVariants(configurationSelector, attributes, state, schema, [] as Set).variants[0]
        variant.metadata.dependencies
    }
}
