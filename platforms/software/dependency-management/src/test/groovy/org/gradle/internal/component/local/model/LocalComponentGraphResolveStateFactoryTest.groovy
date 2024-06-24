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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalConfigurationMetadataBuilder
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.internal.component.ResolutionFailureHandler
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

/**
 * Tests {@link LocalComponentGraphResolveStateFactory}.
 */
class LocalComponentGraphResolveStateFactoryTest extends AbstractProjectBuilderSpec {
    ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    ModuleComponentIdentifier componentIdentifier = DefaultModuleComponentIdentifier.newId(id)

    def metadataBuilder = new DefaultLocalConfigurationMetadataBuilder(
        new TestDependencyMetadataFactory(),
        new DefaultExcludeRuleConverter(new DefaultImmutableModuleIdentifierFactory())
    )

    LocalComponentGraphResolveStateFactory stateFactory = new LocalComponentGraphResolveStateFactory(
        Stub(AttributeDesugaring),
        new ComponentIdGenerator(),
        metadataBuilder,
        TestUtil.calculatedValueContainerFactory()
    )

    LocalComponentGraphResolveState state

    def setup() {
        state = stateFactory.stateFor(
            RootScriptDomainObjectContext.INSTANCE,
            componentIdentifier,
            id,
            project.configurations as ConfigurationsProvider,
            "status",
            EmptySchema.INSTANCE
        )
    }

    def "can lookup configuration after it has been added"() {
        given:
        def parent = dependencyScope("parent")
        consumable("conf", [parent])

        expect:
        state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf", Stub(ResolutionFailureHandler))
    }

    def "configuration has no dependencies or artifacts when none have been added"() {
        given:
        def parent = dependencyScope("parent")
        consumable("conf", [parent])

        when:
        def confState = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf", Stub(ResolutionFailureHandler))

        then:
        confState.metadata.dependencies.empty
        confState.metadata.excludes.empty
        confState.metadata.files.empty

        and:
        confState.resolveArtifacts().artifacts.isEmpty()
        confState.prepareForArtifactResolution().artifactVariants.size() == 1
    }

    def "can lookup artifact in various ways after it has been added"() {
        given:
        def conf = consumable("conf")

        def artifact = artifactName()
        def file = new File("artifact.zip")
        addArtifact(conf, artifact, file)

        when:
        def confState = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf", Stub(ResolutionFailureHandler))

        then:
        confState.resolveArtifacts().artifacts.size() == 1

        def publishArtifact = confState.resolveArtifacts().artifacts.first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == confState.prepareForArtifactResolution().artifactVariants.first().artifacts.first()
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
        def conf1State = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("child1", Stub(ResolutionFailureHandler))
        def conf2State = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("child2", Stub(ResolutionFailureHandler))

        then:
        conf1State.resolveArtifacts().artifacts.size() == 3
        conf2State.resolveArtifacts().artifacts.size() == 1
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
        def conf1State = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf1", Stub(ResolutionFailureHandler))
        def conf2State = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf2", Stub(ResolutionFailureHandler))

        then:
        conf1State.resolveArtifacts().artifacts.size() == 1
        conf1State.resolveArtifacts().artifacts == conf2State.resolveArtifacts().artifacts
    }

    def "artifact has same file as original publish artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        def conf = consumable("conf")

        and:
        addArtifact(conf, artifact, file)

        when:
        def confState = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf", Stub(ResolutionFailureHandler))

        then:
        confState.resolveArtifacts().artifacts.first().file == file
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
        def conf1State = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf1", Stub(ResolutionFailureHandler))
        def conf2State = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf2", Stub(ResolutionFailureHandler))

        then:
        def conf1Artifacts = conf1State.prepareForArtifactResolution().artifactVariants as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = conf2State.prepareForArtifactResolution().artifactVariants as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.identifier != artifactMetadata2.identifier

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
        def config1 = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf1", Stub(ResolutionFailureHandler))
        def config2 = state.candidatesForGraphVariantSelection.getVariantByConfigurationName("conf2", Stub(ResolutionFailureHandler))

        then:
        config1.prepareForArtifactResolution().artifactVariants*.name as List == ["conf1", "conf1-variant1"]
        config1.prepareForArtifactResolution().artifactVariants.find { it.name == "conf1-variant1" }.attributes == AttributeTestUtil.attributes(["foo1": "bar1"])

        config2.prepareForArtifactResolution().artifactVariants*.name as List == ["conf2", "conf2-variant2"]
        config2.prepareForArtifactResolution().artifactVariants.find { it.name == "conf2-variant2" }.attributes == AttributeTestUtil.attributes(["foo2": "bar2"])

        and:
        config1.prepareForArtifactResolution().artifactVariants.find { it.name == "conf1-variant1" }.artifacts.size() == 1
        config2.prepareForArtifactResolution().artifactVariants.find { it.name == "conf2-variant2" }.artifacts.size() == 2
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
        state.getConfiguration('child1').metadata.files*.source == [files1, files2, files3]
        state.getConfiguration('child2').metadata.files*.source == [files1]
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
        state.getConfiguration("child1").metadata.dependencies*.source == [dependency1, dependency2, dependency3]
        state.getConfiguration("child2").metadata.dependencies*.source == [dependency1]
        state.getConfiguration("other").metadata.dependencies.isEmpty()
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        def conf = dependencyScope("conf")
        def child = resolvable("child", [conf])

        conf.exclude([group: "group1", module: "module1"])
        child.exclude([group: "group2", module: "module2"])

        expect:
        def config = state.getConfiguration("child").metadata
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
