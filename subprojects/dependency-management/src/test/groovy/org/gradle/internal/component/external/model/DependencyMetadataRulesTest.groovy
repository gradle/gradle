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
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.model.ComponentAttributeMatcher
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.testing.internal.util.Specification
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Unroll

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class DependencyMetadataRulesTest extends Specification {
    private instantiator = DirectInstantiator.INSTANCE
    private notationParser = DependencyMetadataNotationParser.parser(instantiator)

    @Shared versionIdentifier = new DefaultModuleVersionIdentifier("org.test", "producer", "1.0")
    @Shared componentIdentifier = DefaultModuleComponentIdentifier.newId(versionIdentifier)
    @Shared attributes = TestUtil.attributesFactory().of(Attribute.of("someAttribute", String), "someValue")
    @Shared schema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())
    @Shared defaultVariant

    private ivyComponentMetadata(String[] deps) {
        def dependencies = deps.collect { name ->
            new IvyDependencyMetadata(newSelector("org.test", name, "1.0"), ImmutableListMultimap.of("default", "default"))
        }
        new DefaultMutableIvyModuleResolveMetadata(versionIdentifier, componentIdentifier, dependencies)
    }
    private mavenComponentMetadata(String[] deps) {
        def dependencies = deps.collect { name ->
            new MavenDependencyMetadata(MavenScope.Compile, false, newSelector("org.test", name, "1.0"), [], [])
        }
        new DefaultMutableMavenModuleResolveMetadata(versionIdentifier, componentIdentifier, dependencies)
    }
    private gradleComponentMetadata(String[] deps) {
        def metadata = new DefaultMutableMavenModuleResolveMetadata(versionIdentifier, componentIdentifier)
        //gradle metadata is distinguished from maven POM metadata by explicitly defining variants
        defaultVariant = metadata.addVariant("default", attributes)
        deps.each { name ->
            defaultVariant.addDependency("org.test", name, new DefaultMutableVersionConstraint("1.0"), [])
        }
        metadata
    }

    @Unroll
    def "dependency metadata rules are evaluated once and lazily for #metadataType metadata"() {
        given:
        def rule = Mock(Action)

        when:
        metadataImplementation.addDependencyMetadataRule("default", rule, instantiator, notationParser)
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

    @Unroll
    def "dependency metadata rules are not evaluated if their variant is not selected for #metadataType metadata"() {
        given:
        def rule = Mock(Action)

        when:
        metadataImplementation.addDependencyMetadataRule("anotherVariant", rule, instantiator, notationParser)
        selectTargetConfigurationMetadata(metadataImplementation).dependencies

        then:
        0 * rule.execute(_)

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata()
        "ivy"        | ivyComponentMetadata()
        "gradle"     | gradleComponentMetadata()
    }

    @Unroll
    def "dependencies of selected variant are accessible in dependency metadata rule for #metadataType metadata"() {
        given:
//        addDependency(metadataImplementation, "org.test", "dep1", "1.0")
//        addDependency(metadataImplementation, "org.test", "dep2", "1.0")
        def rule = { dependencies ->
            assert dependencies.size() == 2
            assert dependencies[0].name == "dep1"
            assert dependencies[1].name == "dep2"
        }

        when:
        metadataImplementation.addDependencyMetadataRule("default", rule, instantiator, notationParser)

        then:
        selectTargetConfigurationMetadata(metadataImplementation).dependencies.size() == 2

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata("dep1", "dep2")
        "ivy"        | ivyComponentMetadata("dep1", "dep2")
        "gradle"     | gradleComponentMetadata("dep1", "dep2")
    }

    @Unroll
    def "dependencies of selected variant are modifiable in dependency metadata rule for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            assert dependencies.size() == 1
            dependencies[0].version {
                it.strictly "2.0"
            }
        }

        when:
        metadataImplementation.addDependencyMetadataRule("default", rule, instantiator, notationParser)

        then:
        selectTargetConfigurationMetadata(metadataImplementation).dependencies[0].selector.version == "2.0"
        selectTargetConfigurationMetadata(metadataImplementation).dependencies[0].selector.versionConstraint.rejectedVersions[0] == "]2.0,)"

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata("toModify")
        "ivy"        | ivyComponentMetadata("toModify")
        "gradle"     | gradleComponentMetadata("toModify")
    }

    @Unroll
    def "dependencies added in dependency metadata rules are added to dependency list for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            dependencies.add("org.test:added:1.0")
        }

        when:
        metadataImplementation.addDependencyMetadataRule("default", rule, instantiator, notationParser)

        then:
        selectTargetConfigurationMetadata(metadataImplementation).dependencies.collect { it.selector } == [ newSelector("org.test", "added", "1.0") ]

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata()
        "ivy"        | ivyComponentMetadata()
        "gradle"     | gradleComponentMetadata()
    }

    @Unroll
    def "dependencies removed in dependency metadata rules are removed from dependency list for #metadataType metadata"() {
        given:
        def rule = { dependencies ->
            assert dependencies.size() == 1
            dependencies.removeAll { it.name == "toRemove" }
        }

        when:
        metadataImplementation.addDependencyMetadataRule("default", rule, instantiator, notationParser)

        then:
        selectTargetConfigurationMetadata(metadataImplementation).dependencies.empty

        where:
        metadataType | metadataImplementation
        "maven"      | mavenComponentMetadata("toRemove")
        "ivy"        | ivyComponentMetadata("toRemove")
        "gradle"     | gradleComponentMetadata("toRemove")
    }

    private selectTargetConfigurationMetadata(MutableModuleComponentResolveMetadata targetComponent) {
        selectTargetConfigurationMetadata(targetComponent.asImmutable())
    }

    private selectTargetConfigurationMetadata(ModuleComponentResolveMetadata immutable) {
        def componentIdentifier = DefaultModuleComponentIdentifier.newId("org.test", "consumer", "1.0")
        def consumerIdentifier = DefaultModuleVersionIdentifier.newId(componentIdentifier)
        def componentSelector = newSelector(consumerIdentifier.group, consumerIdentifier.name, new DefaultMutableVersionConstraint(consumerIdentifier.version))
        def consumer = new LocalComponentDependencyMetadata(componentIdentifier, componentSelector, "default", attributes, null, [] as List, [], false, false, true)

        consumer.selectConfigurations(attributes, immutable, schema)[0]
    }
}
