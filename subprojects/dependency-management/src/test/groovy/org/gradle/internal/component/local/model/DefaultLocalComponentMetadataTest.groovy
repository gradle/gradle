/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalConfigurationMetadataBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Consumer

/**
 * Tests {@link DefaultLocalComponentMetadata}.
 *
 * TODO: This class currently tests a lot of the functionality of
 * {@link DefaultLocalConfigurationMetadataBuilder}. That class should either be merged
 * with {@link DefaultLocalComponentMetadata}, or the relevant tests should be moved
 * to the builder's test class.
 */
class DefaultLocalComponentMetadataTest extends Specification {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)

    def metadataBuilder = new DefaultLocalConfigurationMetadataBuilder(
        new TestDependencyMetadataFactory(),
        new DefaultExcludeRuleConverter(new DefaultImmutableModuleIdentifierFactory())
    )

    Map<String, ConfigurationInternal> configurations = [:]
    def configurationsProvider = Mock(ConfigurationsProvider) {
        size() >> { this.configurations.size() }
        getAll() >> { this.configurations.values() }
        visitAll(_) >> { Consumer<ConfigurationInternal> visitor -> this.configurations.values().forEach {visitor.accept(it) } }
        findByName(_) >> { String name -> this.configurations.get(name) }
    }

    def configurationsFactory = new DefaultLocalComponentMetadata.ConfigurationsProviderMetadataFactory(
        configurationsProvider, metadataBuilder, RootScriptDomainObjectContext.INSTANCE, TestUtil.calculatedValueContainerFactory())

    def metadata = new DefaultLocalComponentMetadata(
        id, componentIdentifier, "status", Mock(AttributesSchemaInternal),
        configurationsFactory, null
    )

    def "can lookup configuration after it has been added"() {
        when:
        def parent = addConfiguration("parent")
        addConfiguration("conf", [parent])

        then:
        metadata.configurationNames == ['conf', 'parent'] as Set

        def confMd = metadata.getConfiguration('conf')
        confMd != null
        confMd.hierarchy == ['conf', 'parent'] as Set

        def parentMd = metadata.getConfiguration('parent')
        parentMd != null
        parentMd.hierarchy == ['parent'] as Set
    }

    def "configuration has no dependencies or artifacts when none have been added"() {
        when:
        def parent = addConfiguration("parent")
        addConfiguration("conf", [parent])

        then:
        def confMd = metadata.getConfiguration('conf')
        confMd.dependencies.empty
        confMd.excludes.empty
        confMd.files.empty

        when:
        confMd.prepareToResolveArtifacts()

        then:
        confMd.artifacts.empty
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = addConfiguration("conf")

        when:
        addArtifact(conf, artifact, file)
        metadata.getConfiguration("conf").prepareToResolveArtifacts()

        then:
        metadata.getConfiguration("conf").artifacts.size() == 1

        def publishArtifact = metadata.getConfiguration("conf").artifacts.first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == metadata.getConfiguration("conf").artifact(artifact)
    }

    def "artifact is attached to child configurations"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def artifact3 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")
        def file3 = new File("artifact-3.zip")

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        def child1 = addConfiguration("child1", [conf1, conf2])
        addConfiguration("child2", [conf1])

        when:
        addArtifact(conf1, artifact1, file1)
        addArtifact(conf2, artifact2, file2)
        addArtifact(child1, artifact3, file3)
        metadata.getConfiguration("conf1").prepareToResolveArtifacts()
        metadata.getConfiguration("child1").prepareToResolveArtifacts()
        metadata.getConfiguration("child2").prepareToResolveArtifacts()

        then:
        metadata.getConfiguration("conf1").artifacts.size() == 1
        metadata.getConfiguration("child1").artifacts.size() == 3
        metadata.getConfiguration("child2").artifacts.size() == 1
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")

        when:
        def publishArtifact = new DefaultPublishArtifact(artifact.name, artifact.extension, artifact.type, artifact.classifier, new Date(), file)
        conf1.artifacts.add(publishArtifact)
        conf2.artifacts.add(publishArtifact)
        metadata.getConfiguration("conf1").prepareToResolveArtifacts()
        metadata.getConfiguration("conf2").prepareToResolveArtifacts()

        then:
        metadata.getConfiguration("conf1").artifacts.size() == 1
        metadata.getConfiguration("conf1").artifacts == metadata.getConfiguration("conf2").artifacts
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = addConfiguration("conf")

        and:
        addArtifact(conf, artifact, file)
        metadata.getConfiguration("conf").prepareToResolveArtifacts()

        expect:
        def ivyArtifact = artifactName()
        def resolveArtifact = metadata.getConfiguration("conf").artifact(ivyArtifact)
        resolveArtifact.file == file
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifactName()

        given:
        addConfiguration("conf")

        when:
        metadata.getConfiguration("conf").prepareToResolveArtifacts()

        then:
        def resolveArtifact = metadata.getConfiguration("conf").artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        addArtifact(conf1, artifact1, file1)
        addArtifact(conf2, artifact2, file2)

        when:
        metadata.getConfiguration("conf1").prepareToResolveArtifacts()
        metadata.getConfiguration("conf2").prepareToResolveArtifacts()

        then:
        def conf1Artifacts = metadata.getConfiguration("conf1").artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = metadata.getConfiguration("conf2").artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        metadata.getConfiguration("conf1").artifacts == [artifactMetadata1]
        metadata.getConfiguration("conf2").artifacts == [artifactMetadata2]
    }

    def "variants are attached to configuration but not its children"() {
        def variant1Attrs = Stub(ImmutableAttributes)
        def variant2Attrs = Stub(ImmutableAttributes)

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2", [conf1])
        ConfigurationVariant variant1 = conf1.outgoing.getVariants().create("variant1")
        ConfigurationVariant variant2 = conf2.outgoing.getVariants().create("variant2")
        variant1.attributes >> variant1Attrs
        variant2.attributes >> variant2Attrs
        variant1.artifacts.add(Stub(PublishArtifact))
        variant2.artifacts.add(Stub(PublishArtifact))
        variant2.artifacts.add(Stub(PublishArtifact))

        when:
        def config1 = metadata.getConfiguration("conf1")
        def config2 = metadata.getConfiguration("conf2")

        then:
        config1.variants*.name as List == ["conf1", "conf1-variant1"]
        config1.variants.find { it.name == "conf1-variant1" }.attributes == variant1Attrs

        config2.variants*.name as List == ["conf2", "conf2-variant2"]
        config2.variants.find { it.name == "conf2-variant2" }.attributes == variant2Attrs

        when:
        config1.prepareToResolveArtifacts()
        config2.prepareToResolveArtifacts()

        then:
        config1.variants.find { it.name == "conf1-variant1" }.artifacts.size() == 1
        config2.variants.find { it.name == "conf2-variant2" }.artifacts.size() == 2
    }

    def "files attached to configuration and its children"() {
        def files1 = Stub(FileCollectionDependency)
        def files2 = Stub(FileCollectionDependency)
        def files3 = Stub(FileCollectionDependency)

        given:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        def child1 = addConfiguration("child1", [conf1, conf2])
        addConfiguration("child2", [conf1])

        when:
        conf1.getDependencies().add(files1)
        conf2.getDependencies().add(files2)
        child1.getDependencies().add(files3)

        then:
        metadata.getConfiguration("conf1").files*.source == [files1]
        metadata.getConfiguration("conf2").files*.source == [files2]
        metadata.getConfiguration("child1").files*.source == [files1, files2, files3]
        metadata.getConfiguration("child2").files*.source == [files1]
    }

    def "dependency is attached to configuration and its children"() {
        def dependency1 = Mock(ExternalModuleDependency)
        def dependency2 = Mock(ExternalModuleDependency)
        def dependency3 = Mock(ExternalModuleDependency)

        when:
        def conf1 = addConfiguration("conf1")
        def conf2 = addConfiguration("conf2")
        def child1 = addConfiguration("child1", [conf1, conf2])
        addConfiguration("child2", [conf1])
        addConfiguration("other")

        conf1.getDependencies().add(dependency1)
        conf2.getDependencies().add(dependency2)
        child1.getDependencies().add(dependency3)

        then:
        metadata.getConfiguration("conf1").dependencies*.source == [dependency1]
        metadata.getConfiguration("conf2").dependencies*.source == [dependency2]
        metadata.getConfiguration("child1").dependencies*.source == [dependency1, dependency2, dependency3]
        metadata.getConfiguration("child2").dependencies*.source == [dependency1]
        metadata.getConfiguration("other").dependencies.isEmpty()
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        def compile = addConfiguration("compile")
        def runtime = addConfiguration("runtime", [compile])

        compile.getExcludeRules().add(new DefaultExcludeRule("group1", "module1"))
        runtime.getExcludeRules().add(new DefaultExcludeRule("group2", "module2"))

        expect:
        def config = metadata.getConfiguration("runtime")
        def excludes = config.excludes
        config.excludes*.moduleId.group == ["group1", "group2"]
        config.excludes*.moduleId.name == ["module1", "module2"]
        config.excludes.is(excludes)
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }

    ConfigurationInternal addConfiguration(String name, List<ConfigurationInternal> extendsFrom = []) {
        def conf = configuration(name, extendsFrom)
        configurations.put(name, conf)
        return conf
    }

    /**
     * Creates a minimal mocked {@link ConfigurationInternal} instance.
     *
     * TODO: We really should not need such a complex Configuration mock here. However, since some of
     * the tests here are testing functionality of {@link DefaultLocalConfigurationMetadataBuilder}, this
     * complex Configuration mock is required.
     *
     * TODO: And TBH if we're doing this much mocking, we really should be using real Configurations.
     */
    ConfigurationInternal configuration(String name, List<ConfigurationInternal> extendsFrom) {

        DependencySet dependencies = new DefaultDependencySet(Describables.of("dependencies"), Mock(ConfigurationInternal) {
            isCanBeDeclared() >> true
        }, TestUtil.domainObjectCollectionFactory().newDomainObjectSet(Dependency))

        DependencyConstraintSet dependencyConstraints = Mock() {
            iterator() >> Collections.emptyIterator()
        }

        NamedDomainObjectContainer<ConfigurationVariant> variants = TestUtil.domainObjectCollectionFactory()
            .newNamedDomainObjectContainer(ConfigurationVariant.class, variantName -> Mock(ConfigurationVariant) {
                getName() >> variantName
                getArtifacts() >> artifactSet()
            })

        PublishArtifactSet artifacts = artifactSet()
        ConfigurationPublications outgoing = Mock(ConfigurationPublications) {
            getCapabilities() >> Collections.emptySet()
            getArtifacts() >> artifacts
            getVariants() >> variants
        }

        def conf = Mock(ConfigurationInternal) {
            getName() >> name
            getAttributes() >> ImmutableAttributes.EMPTY
            isCanBeDeclared() >> true
            getDependencies() >> dependencies
            getDependencyConstraints() >> dependencyConstraints
            getExcludeRules() >> new LinkedHashSet<ExcludeRule>()
            collectVariants(_ as ConfigurationInternal.VariantVisitor) >> { ConfigurationInternal.VariantVisitor visitor ->
                visitor.visitArtifacts(artifacts)
                visitor.visitOwnVariant(Describables.of(name), ImmutableAttributes.EMPTY, Collections.emptySet(), artifacts)
                variants.each {
                    visitor.visitChildVariant(it.name, Describables.of(it.name), it.attributes as ImmutableAttributes, Collections.emptySet(), it.artifacts)
                }
            }
            getOutgoing() >> outgoing
            getExtendsFrom() >> extendsFrom
            getArtifacts() >> artifacts
        }
        conf.getHierarchy() >> [conf] + extendsFrom
        return conf
    }

    void addArtifact(ConfigurationInternal configuration, IvyArtifactName name, File file) {
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name.name, name.extension, name.type, name.classifier, new Date(), file)

        configuration.artifacts.add(publishArtifact)
    }

    PublishArtifactSet artifactSet() {
        new DefaultPublishArtifactSet(
            Describables.of("artifacts"),
            TestUtil.domainObjectCollectionFactory().newDomainObjectSet(PublishArtifact),
            TestFiles.fileCollectionFactory(),
            Mock(TaskDependencyFactory)
        )
    }

    LocalOriginDependencyMetadata dependencyMetadata(Dependency dependency) {
        return new DslOriginDependencyMetadataWrapper(Mock(LocalOriginDependencyMetadata), dependency)
    }

    class TestDependencyMetadataFactory implements DependencyMetadataFactory {
        @Override
        LocalOriginDependencyMetadata createDependencyMetadata(ComponentIdentifier componentId, String clientConfiguration, AttributeContainer attributes, ModuleDependency dependency) {
            return dependencyMetadata(dependency)
        }

        @Override
        LocalOriginDependencyMetadata createDependencyConstraintMetadata(ComponentIdentifier componentId, String clientConfiguration, AttributeContainer attributes, DependencyConstraint dependencyConstraint) {
            throw new UnsupportedOperationException()
        }
    }
}
