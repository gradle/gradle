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

package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableListMultimap
import org.gradle.api.Action
import org.gradle.api.artifacts.DependenciesMetadata
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

abstract class AbstractDependencyMetadataRulesTest extends Specification {
    def instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def notationParser = DependencyMetadataNotationParser.parser(instantiator, DirectDependencyMetadataImpl, SimpleMapInterner.notThreadSafe())
    def constraintNotationParser = DependencyMetadataNotationParser.parser(instantiator, DependencyConstraintMetadataImpl, SimpleMapInterner.notThreadSafe())

    @Shared
        versionIdentifier = new DefaultModuleVersionIdentifier("org.test", "producer", "1.0")
    @Shared
        componentIdentifier = DefaultModuleComponentIdentifier.newId(versionIdentifier)
    @Shared
        attributes = AttributeTestUtil.attributesFactory().of(Attribute.of("someAttribute", String), "someValue")
    @Shared
        mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    @Shared
        ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    @Shared
        defaultVariant

    protected static <T> VariantMetadataRules.VariantAction<T> variantAction(String variantName, Action<? super T> action) {
        new VariantMetadataRules.VariantAction<T>(variantName, action)
    }

    abstract boolean addAllDependenciesAsConstraints()

    abstract void doAddDependencyMetadataRule(MutableModuleComponentResolveMetadata metadataImplementation, String variantName = null, Action<? super DependenciesMetadata> action)

    boolean supportedInMetadata(String metadata) {
        !addAllDependenciesAsConstraints() || metadata == "gradle"
    }

    private ivyComponentMetadata(String[] deps) {
        def dependencies
        if (addAllDependenciesAsConstraints()) {
            dependencies = [] //not supported in Ivy metadata
        } else {
            dependencies = deps.collect { name ->
                new IvyDependencyDescriptor(newSelector(DefaultModuleIdentifier.newId("org.test", name), "1.0"), ImmutableListMultimap.of("default", "default"))
            }
        }
        ivyMetadataFactory.create(componentIdentifier, dependencies)
    }

    private mavenComponentMetadata(String[] deps) {
        def dependencies = deps.collect { name ->
            MavenDependencyType type = addAllDependenciesAsConstraints() ? MavenDependencyType.OPTIONAL_DEPENDENCY : MavenDependencyType.DEPENDENCY
            new MavenDependencyDescriptor(MavenScope.Compile, type, newSelector(DefaultModuleIdentifier.newId("org.test", name), "1.0"), null, [])
        }
        mavenMetadataFactory.create(componentIdentifier, dependencies)
    }

    private gradleComponentMetadata(String[] deps) {
        def metadata = mavenMetadataFactory.create(componentIdentifier, [])
        //gradle metadata is distinguished from maven POM metadata by explicitly defining variants
        defaultVariant = metadata.addVariant("default", attributes)
        deps.each { name ->
            if (addAllDependenciesAsConstraints()) {
                defaultVariant.addDependencyConstraint("org.test", name, new DefaultMutableVersionConstraint("1.0"), null, ImmutableAttributes.EMPTY)
            } else {
                defaultVariant.addDependency("org.test", name, new DefaultMutableVersionConstraint("1.0"), [], null, ImmutableAttributes.EMPTY, [], false, null)
            }
        }
        metadata
    }

    def "dependency metadata rules are evaluated once and lazily for #metadataType metadata"() {
        given:
        def rule = Mock(Action)

        when:
        doAddDependencyMetadataRule(metadataImplementation, rule)
        def metadata = metadataImplementation.asImmutable()

        then:
        0 * rule.execute(_)

        when:
        selectTargetConfigurationMetadata(metadata).dependencies

        then:
        1 * rule.execute(_)

        when:
        selectTargetConfigurationMetadata(metadata).dependencies
        selectTargetConfigurationMetadata(metadata).dependencies
        selectTargetConfigurationMetadata(metadata).dependencies

        then:
        0 * rule.execute(_)

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata()
        "ivy"        | ivyComponentMetadata()
        "gradle"     | gradleComponentMetadata()
    }

    def "dependency metadata rules are not evaluated if their variant is not selected for #metadataType metadata"() {
        given:
        def rule = Mock(Action)

        when:
        doAddDependencyMetadataRule(metadataImplementation, "anotherVariant", rule)
        selectTargetConfigurationMetadata(metadataImplementation).dependencies

        then:
        0 * rule.execute(_)

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata()
        "ivy"        | ivyComponentMetadata()
        "gradle"     | gradleComponentMetadata()
    }

    def "dependencies of selected variant are accessible in dependency metadata rule for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            if (supportedInMetadata(metadataType)) {
                assert dependencies.size() == 2
                assert dependencies[0].name == "dep1"
                assert dependencies[1].name == "dep2"
            } else {
                assert dependencies.empty
            }
        }

        when:
        doAddDependencyMetadataRule(metadataImplementation, rule)
        def dependencies = selectTargetConfigurationMetadata(metadataImplementation).dependencies

        then:
        if (supportedInMetadata(metadataType)) {
            assert dependencies.size() == 2
            assert dependencies[0].constraint == addAllDependenciesAsConstraints()
            assert dependencies[1].constraint == addAllDependenciesAsConstraints()
        } else {
            assert dependencies.empty
        }

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata("dep1", "dep2")
        "ivy"        | ivyComponentMetadata("dep1", "dep2")
        "gradle"     | gradleComponentMetadata("dep1", "dep2")
    }

    def "dependencies of selected variant are modifiable in dependency metadata rule for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            if (supportedInMetadata(metadataType)) {
                assert dependencies.size() == 1
                dependencies[0].version {
                    it.strictly "2.0"
                    it.reject "[3.0,)"
                }
            } else {
                assert dependencies.empty
            }
        }

        when:
        doAddDependencyMetadataRule(metadataImplementation, rule)
        def dependencies = selectTargetConfigurationMetadata(metadataImplementation).dependencies

        then:
        if (supportedInMetadata(metadataType)) {
            assert dependencies[0].selector.version == "2.0"
            assert dependencies[0].selector.versionConstraint.strictVersion == "2.0"
            assert dependencies[0].selector.versionConstraint.rejectedVersions[0] == "[3.0,)"
            assert dependencies[0].constraint == addAllDependenciesAsConstraints()
        } else {
            assert dependencies.empty
        }

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata("toModify")
        "ivy"        | ivyComponentMetadata("toModify")
        "gradle"     | gradleComponentMetadata("toModify")
    }

    def "dependencies added in dependency metadata rules are added to dependency list for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            dependencies.add("org.test:added:1.0")
        }

        when:
        doAddDependencyMetadataRule(metadataImplementation, rule)
        def dependencies = selectTargetConfigurationMetadata(metadataImplementation).dependencies

        then:
        dependencies.collect { it.selector } == [newSelector(DefaultModuleIdentifier.newId("org.test", "added"), "1.0")]

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata()
        "ivy"        | ivyComponentMetadata()
        "gradle"     | gradleComponentMetadata()
    }

    def "dependencies removed in dependency metadata rules are removed from dependency list for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            assert dependencies.size() == (supportedInMetadata(metadataType) ? 1 : 0)
            dependencies.removeAll { it.name == "toRemove" }
        }

        when:
        doAddDependencyMetadataRule(metadataImplementation, rule)
        def dependencies = selectTargetConfigurationMetadata(metadataImplementation).dependencies

        then:
        dependencies.empty

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata("toRemove")
        "ivy"        | ivyComponentMetadata("toRemove")
        "gradle"     | gradleComponentMetadata("toRemove")
    }

    VariantGraphResolveMetadata selectTargetConfigurationMetadata(MutableModuleComponentResolveMetadata targetComponent) {
        return selectTargetConfigurationMetadata(targetComponent.asImmutable())
    }

    VariantGraphResolveMetadata selectTargetConfigurationMetadata(ModuleComponentResolveMetadata immutable) {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.test", "consumer"), "1.0")
        def consumerIdentifier = DefaultModuleVersionIdentifier.newId(componentIdentifier)
        def componentSelector = newSelector(consumerIdentifier.module, new DefaultMutableVersionConstraint(consumerIdentifier.version))
        def consumer = new LocalComponentDependencyMetadata(componentSelector, null, [] as List, [], false, false, true, false, false, null)
        def state = DependencyManagementTestUtil.modelGraphResolveFactory().stateFor(immutable)
        def variantSelector = new GraphVariantSelector(DependencyManagementTestUtil.newFailureHandler())
        def schema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        return consumer.selectVariants(variantSelector, attributes, state, schema, [] as Set).variants[0].metadata
    }
}
