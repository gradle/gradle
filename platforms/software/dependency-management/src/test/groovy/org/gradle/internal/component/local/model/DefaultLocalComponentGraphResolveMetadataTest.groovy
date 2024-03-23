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

import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantMetadataBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

/**
 * Tests {@link DefaultLocalComponentGraphResolveMetadata}.
 *
 * TODO: This class currently tests a lot of the functionality of
 * {@link DefaultLocalVariantMetadataBuilder}. That class should either be merged
 * with {@link DefaultLocalComponentGraphResolveMetadata}, or the relevant tests should be moved
 * to the builder's test class.
 */
class DefaultLocalComponentGraphResolveMetadataTest extends AbstractProjectBuilderSpec {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)

    def metadataBuilder = new DefaultLocalVariantMetadataBuilder(
        new TestDependencyMetadataFactory(),
        new DefaultExcludeRuleConverter(new DefaultImmutableModuleIdentifierFactory())
    )

    DefaultLocalComponentGraphResolveMetadata.VariantMetadataFactory configurationsFactory
    DefaultLocalComponentGraphResolveMetadata metadata

    def setup() {
        configurationsFactory = new DefaultLocalComponentGraphResolveMetadata.ConfigurationsProviderMetadataFactory(
            componentIdentifier,
            project.configurations as ConfigurationsProvider,
            metadataBuilder,
            RootScriptDomainObjectContext.INSTANCE,
            TestUtil.calculatedValueContainerFactory()
        )
        metadata = new DefaultLocalComponentGraphResolveMetadata(
            id, componentIdentifier, "status", Mock(AttributesSchemaInternal),
            configurationsFactory, null
        )
    }

    def "can lookup configuration after it has been added"() {
        given:
        def parent = dependencyScope("parent")
        consumable("conf", [parent])

        expect:
        metadata.variantsForGraphTraversal.find { it -> it.configurationName == 'conf' }
    }

    def "configuration has no dependencies or artifacts when none have been added"() {
        given:
        def parent = dependencyScope("parent")
        consumable("conf", [parent])

        when:
        def confMd = metadata.variantsForGraphTraversal.find { it -> it.configurationName == 'conf' }

        then:
        confMd.dependencies.empty
        confMd.excludes.empty
        confMd.files.empty

        when:
        def artifactState = confMd.prepareToResolveArtifacts()

        then:
        artifactState.artifacts.isEmpty()
    }

    def "can lookup artifact in various ways after it has been added"() {
        given:
        def conf = consumable("conf")

        def artifact = artifactName()
        def file = new File("artifact.zip")
        addArtifact(conf, artifact, file)

        when:
        def confMd = metadata.variantsForGraphTraversal.find { it -> it.configurationName == 'conf' }

        then:
        confMd.prepareToResolveArtifacts().artifacts.size() == 1

        def publishArtifact = confMd.prepareToResolveArtifacts().artifacts.first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == confMd.prepareToResolveArtifacts().variants.first().artifacts.first()
    }

    def "artifact is attached to child configurations"() {
        given:
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def artifact3 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")
        def file3 = new File("artifact-3.zip")

        def conf1 = dependencyScope("conf1")
        def conf2 = dependencyScope("conf2")
        def child1 = consumable("child1", [conf1, conf2])
        consumable("child2", [conf1])

        addArtifact(conf1, artifact1, file1)
        addArtifact(conf2, artifact2, file2)
        addArtifact(child1, artifact3, file3)

        when:
        def child1Md = metadata.variantsForGraphTraversal.find { it.configurationName == "child1" }
        def child2Md = metadata.variantsForGraphTraversal.find { it.configurationName == "child2" }

        then:
        child1Md.prepareToResolveArtifacts().artifacts.size() == 3
        child2Md.prepareToResolveArtifacts().artifacts.size() == 1
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf1 = consumable("conf1")
        def conf2 = consumable("conf2")

        def publishArtifact = new DefaultPublishArtifact(artifact.name, artifact.extension, artifact.type, artifact.classifier, new Date(), file)
        conf1.artifacts.add(publishArtifact)
        conf2.artifacts.add(publishArtifact)

        when:
        def conf1Md = metadata.variantsForGraphTraversal.find { it.configurationName == "conf1" }
        def conf2Md = metadata.variantsForGraphTraversal.find { it.configurationName == "conf2" }

        then:
        conf1Md.prepareToResolveArtifacts().artifacts.size() == 1
        conf1Md.prepareToResolveArtifacts().artifacts == conf2Md.prepareToResolveArtifacts().artifacts
    }

    def "artifact has same file as original publish artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = consumable("conf")

        and:
        addArtifact(conf, artifact, file)

        when:
        def confMd = metadata.variantsForGraphTraversal.find { it -> it.configurationName == 'conf' }

        then:
        confMd.prepareToResolveArtifacts().artifacts.first().file == file
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        given:
        def conf1 = consumable("conf1")
        def conf2 = consumable("conf2")
        addArtifact(conf1, artifact1, file1)
        addArtifact(conf2, artifact2, file2)

        when:
        def conf1Md = metadata.variantsForGraphTraversal.find { it.configurationName == "conf1" }
        def conf2Md = metadata.variantsForGraphTraversal.find { it.configurationName == "conf2" }

        then:
        def conf1Artifacts = conf1Md.prepareToResolveArtifacts().artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = conf2Md.prepareToResolveArtifacts().artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        conf1Artifacts == [artifactMetadata1]
        conf2Artifacts == [artifactMetadata2]
    }

    def "variants are attached to configuration but not its children"() {
        given:
        def conf1 = consumable("conf1")
        def conf2 = consumable("conf2", [conf1])

        conf1.artifacts.add(Mock(PublishArtifact))
        ConfigurationVariant variant1 = conf1.outgoing.getVariants().create("variant1")
        variant1.attributes.attribute(Attribute.of("foo1", String), "bar1")
        variant1.artifacts.add(Stub(PublishArtifact))

        conf2.artifacts.add(Mock(PublishArtifact))
        ConfigurationVariant variant2 = conf2.outgoing.getVariants().create("variant2")
        variant2.attributes.attribute(Attribute.of("foo2", String), "bar2")
        variant2.artifacts.add(Stub(PublishArtifact))
        variant2.artifacts.add(Stub(PublishArtifact))

        when:
        def config1 = metadata.variantsForGraphTraversal.find { it.configurationName == "conf1" }
        def config2 = metadata.variantsForGraphTraversal.find { it.configurationName == "conf2" }

        then:
        config1.prepareToResolveArtifacts().variants*.name as List == ["conf1", "conf1-variant1"]
        config1.prepareToResolveArtifacts().variants.find { it.name == "conf1-variant1" }.attributes == AttributeTestUtil.attributes(["foo1": "bar1"])

        config2.prepareToResolveArtifacts().variants*.name as List == ["conf2", "conf2-variant2"]
        config2.prepareToResolveArtifacts().variants.find { it.name == "conf2-variant2" }.attributes == AttributeTestUtil.attributes(["foo2": "bar2"])

        and:
        config1.prepareToResolveArtifacts().variants.find { it.name == "conf1-variant1" }.artifacts.size() == 1
        config2.prepareToResolveArtifacts().variants.find { it.name == "conf2-variant2" }.artifacts.size() == 2
    }

    def "files attached to configuration and its children"() {
        def files1 = Stub(FileCollectionDependency)
        def files2 = Stub(FileCollectionDependency)
        def files3 = Stub(FileCollectionDependency)

        given:
        def conf1 = dependencyScope("conf1")
        def conf2 = dependencyScope("conf2")
        def conf3 = dependencyScope("conf3", [conf1, conf2])
        resolvable("child1", [conf3])
        resolvable("child2", [conf1])

        and:
        conf1.getDependencies().add(files1)
        conf2.getDependencies().add(files2)
        conf3.getDependencies().add(files3)

        expect:
        metadata.getRootVariant('child1').files*.source == [files1, files2, files3]
        metadata.getRootVariant('child2').files*.source == [files1]
    }

    def "dependency is attached to configuration and its children"() {
        def dependency1 = Mock(ExternalModuleDependency)
        def dependency2 = Mock(ExternalModuleDependency)
        def dependency3 = Mock(ExternalModuleDependency)

        when:
        def conf1 = dependencyScope("conf1")
        def conf2 = dependencyScope("conf2")
        def conf3 = dependencyScope("conf3", [conf1, conf2])
        resolvable("child1", [conf3])
        resolvable("child2", [conf1])
        resolvable("other")

        conf1.getDependencies().add(dependency1)
        conf2.getDependencies().add(dependency2)
        conf3.getDependencies().add(dependency3)

        then:
        metadata.getRootVariant("child1").dependencies*.source == [dependency1, dependency2, dependency3]
        metadata.getRootVariant("child2").dependencies*.source == [dependency1]
        metadata.getRootVariant("other").dependencies.isEmpty()
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        def conf = dependencyScope("conf")
        def child = resolvable("child", [conf])

        conf.exclude([group: "group1", module: "module1"])
        child.exclude([group: "group2", module: "module2"])

        expect:
        def config = metadata.getRootVariant("child")
        def excludes = config.excludes
        config.excludes*.moduleId.group == ["group2", "group1"]
        config.excludes*.moduleId.name == ["module2", "module1"]
        config.excludes.is(excludes)
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }

    ConfigurationInternal consumable(String name, List<ConfigurationInternal> extendsFrom = []) {
        project.configurations.consumable(name) { conf ->
            extendsFrom.each { conf.extendsFrom(it) }
        }.get() as ConfigurationInternal
    }

    ConfigurationInternal resolvable(String name, List<ConfigurationInternal> extendsFrom = []) {
        project.configurations.resolvable(name) { conf ->
            extendsFrom.each { conf.extendsFrom(it) }
        }.get() as ConfigurationInternal
    }

    ConfigurationInternal dependencyScope(String name, List<ConfigurationInternal> extendsFrom = []) {
        project.configurations.dependencyScope(name) { conf ->
            extendsFrom.each { conf.extendsFrom(it) }
        }.get() as ConfigurationInternal
    }

    void addArtifact(ConfigurationInternal configuration, IvyArtifactName name, File file) {
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name.name, name.extension, name.type, name.classifier, new Date(), file)
        configuration.artifacts.add(publishArtifact)
    }

    LocalOriginDependencyMetadata dependencyMetadata(Dependency dependency) {
        return new DslOriginDependencyMetadataWrapper(Mock(LocalOriginDependencyMetadata), dependency)
    }

    class TestDependencyMetadataFactory implements DependencyMetadataFactory {
        @Override
        LocalOriginDependencyMetadata createDependencyMetadata(ModuleDependency dependency) {
            return dependencyMetadata(dependency)
        }

        @Override
        LocalOriginDependencyMetadata createDependencyConstraintMetadata(DependencyConstraint dependencyConstraint) {
            throw new UnsupportedOperationException()
        }
    }
}
