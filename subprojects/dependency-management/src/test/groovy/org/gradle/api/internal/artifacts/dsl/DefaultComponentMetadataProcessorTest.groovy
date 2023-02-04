/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter
import org.gradle.api.specs.Specs
import org.gradle.cache.internal.DefaultInMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ivy.DefaultMutableIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.DefaultMutableMavenModuleResolveMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.rules.DefaultRuleActionAdapter
import org.gradle.internal.rules.DefaultRuleActionValidator
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Specification

class DefaultComponentMetadataProcessorTest extends Specification {

    private static final String GROUP = "group"
    private static final String MODULE = "module"
    static def ruleActionAdapter = new DefaultRuleActionAdapter(new DefaultRuleActionValidator(), "context")
    private static SpecRuleAction<ComponentMetadataDetails> rule1 = new SpecRuleAction(ruleActionAdapter.createFromAction(new Action<ComponentMetadataDetails>() {
        @Override
        void execute(ComponentMetadataDetails t) {
            rule1Executed = true
        }
    }), Specs.satisfyAll())
    private static SpecRuleAction<ComponentMetadataDetails> rule2 = new SpecRuleAction(ruleActionAdapter.createFromAction(new Action<ComponentMetadataDetails>() {
        @Override
        void execute(ComponentMetadataDetails t) {
            rule2Executed = true
        }
    }), Specs.satisfyAll())

    private static boolean rule1Executed
    private static boolean rule2Executed

    MetadataResolutionContext context = Mock()
    def executor = new ComponentMetadataRuleExecutor(Stub(GlobalScopedCacheBuilderFactory), Stub(DefaultInMemoryCacheDecoratorFactory), Stub(ValueSnapshotter), Stub(BuildCommencedTimeProvider), Stub(Serializer))
    def instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def stringInterner = SimpleMapInterner.notThreadSafe()
    def mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    def ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    def dependencyMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DirectDependencyMetadataImpl, stringInterner)
    def dependencyConstraintMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DependencyConstraintMetadataImpl, stringInterner)
    def moduleIdentifierNotationParser = NotationParserBuilder.toType(ModuleIdentifier).converter(new ModuleIdentifierNotationConverter(new DefaultImmutableModuleIdentifierFactory())).toComposite();
    def componentIdentifierNotationParser = new ComponentIdentifierParserFactory().create()
    def metadataRuleContainer = new ComponentMetadataRuleContainer()

    def 'setup'() {
        rule1Executed = false
        rule2Executed = false
        TestComponentMetadataRule.instanceCount = 0
        TestComponentMetadataRuleWithArgs.instanceCount = 0
        TestComponentMetadataRuleWithArgs.constructorParams = null
    }

    def "does nothing when no rules registered"() {
        def processor = new DefaultComponentMetadataProcessor(metadataRuleContainer, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, AttributeTestUtil.attributesFactory(), executor, DependencyManagementTestUtil.platformSupport(), context)
        def metadata = ivyMetadata().asImmutable()

        expect:
        processor.processMetadata(metadata).is(metadata)
    }

    def "instantiates class rule when processing metadata"() {
        given:
        context.injectingInstantiator >> instantiator
        String notation = "${GROUP}:${MODULE}"
        addRuleForModule(notation)
        def processor = new DefaultComponentMetadataProcessor(metadataRuleContainer, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, AttributeTestUtil.attributesFactory(), executor, DependencyManagementTestUtil.platformSupport(), context)


        when:
        processor.processMetadata(ivyMetadata().asImmutable())

        then:
        TestComponentMetadataRule.instanceCount == 1
    }

    def "instantiates class rule with params when processing metadata"() {
        given:
        context.injectingInstantiator >> instantiator
        String notation = "${GROUP}:${MODULE}"
        addRuleForModuleWithParams(notation, "foo", 42L)

        def processor = new DefaultComponentMetadataProcessor(metadataRuleContainer, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, AttributeTestUtil.attributesFactory(), executor, DependencyManagementTestUtil.platformSupport(), context)

        when:
        processor.processMetadata(ivyMetadata().asImmutable())

        then:
        TestComponentMetadataRuleWithArgs.instanceCount == 1
        TestComponentMetadataRuleWithArgs.constructorParams == ["foo", 42L] as Object[]
    }

    def "processing fails when status is not present in status scheme"() {
        def processor = new DefaultComponentMetadataProcessor(metadataRuleContainer, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, AttributeTestUtil.attributesFactory(), executor, DependencyManagementTestUtil.platformSupport(), context)
        def metadata = ivyMetadata()
        metadata.status = "green"
        metadata.statusScheme = ["alpha", "beta"]

        when:
        processor.processMetadata(metadata.asImmutable())

        then:
        ModuleVersionResolveException e = thrown()
        e.message == /Unexpected status 'green' specified for group:module:version. Expected one of: [alpha, beta]/
    }

    def "process different type rules whatever addition order"() {
        given:
        context.injectingInstantiator >> instantiator
        String notation = "${GROUP}:${MODULE}"
        for (Object rule : rules) {
            if (rule instanceof SpecRuleAction) {
                metadataRuleContainer.addRule(rule)
            } else {
                addRuleForModule(notation)
            }
        }
        def processor = new DefaultComponentMetadataProcessor(metadataRuleContainer, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, AttributeTestUtil.attributesFactory(), executor, DependencyManagementTestUtil.platformSupport(), context)


        when:
        processor.processMetadata(ivyMetadata().asImmutable())

        then:
        TestComponentMetadataRule.instanceCount == 2
        rule1Executed
        rule2Executed

        where:
        rules << [rule1, rule2, "classRule1", "classRule2"].permutations()

    }

    private SpecConfigurableRule addRuleForModule(String notation) {
        metadataRuleContainer.addClassRule(new SpecConfigurableRule(DefaultConfigurableRule.of(TestComponentMetadataRule), new DefaultComponentMetadataHandler.ModuleVersionIdentifierSpec(moduleIdentifierNotationParser.parseNotation(notation))))
    }

    private SpecConfigurableRule addRuleForModuleWithParams(String notation, Object... params) {
        metadataRuleContainer.addClassRule(new SpecConfigurableRule(DefaultConfigurableRule.of(TestComponentMetadataRuleWithArgs, {
            it.params(params)
        } as Action<ActionConfiguration>, SnapshotTestUtil.isolatableFactory()), new DefaultComponentMetadataHandler.ModuleVersionIdentifierSpec(moduleIdentifierNotationParser.parseNotation(notation))))
    }

    private DefaultMutableIvyModuleResolveMetadata ivyMetadata() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata = ivyMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"), [])
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        return metadata
    }

    private DefaultMutableMavenModuleResolveMetadata mavenMetadata() {
        def module = DefaultModuleIdentifier.newId("group", "module")
        def metadata = mavenMetadataFactory.create(DefaultModuleComponentIdentifier.newId(module, "version"), [])
        metadata.status = "integration"
        metadata.statusScheme = ["integration", "release"]
        return metadata
    }
}
