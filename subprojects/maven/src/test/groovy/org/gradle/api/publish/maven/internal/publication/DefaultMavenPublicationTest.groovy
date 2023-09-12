/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.maven.internal.publication

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Category
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.component.DefaultSoftwareComponentVariant
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.internal.PublicationArtifactInternal
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultMavenPublicationTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    DependencyMetaDataProvider module
    NotationParser<Object, MavenArtifact> notationParser = Mock(NotationParser)
    def projectDependencyResolver = Mock(ProjectDependencyPublicationResolver)
    TestFile pomDir
    TestFile pomFile
    TestFile gradleMetadataFile
    File artifactFile

    def "setup"() {
        module = Mock(DependencyMetaDataProvider) {
            getModule() >> new DefaultModule("group", "name", "version")
        }
        pomDir = testDirectoryProvider.testDirectory
        pomFile = pomDir.createFile("pom-file")
        gradleMetadataFile = pomDir.createFile("module-file")
        artifactFile = pomDir.createFile("artifact-file")
        artifactFile << "some content"
    }

    def "name and identity properties are passed through"() {
        when:
        def publication = createPublication()

        then:
        publication.name == "pub-name"
        publication.pom.coordinates.groupId.get() == "group"
        publication.pom.coordinates.artifactId.get() == "name"
        publication.pom.coordinates.version.get() == "version"
    }

    def "publication coordinates are live"() {
        when:
        def publication = createPublication()

        and:
        publication.groupId = "group2"
        publication.artifactId = "name2"
        publication.version = "version2"

        then:
        publication.pom.coordinates.groupId.get() == "group2"
        publication.pom.coordinates.artifactId.get() == "name2"
        publication.pom.coordinates.version.get() == "version2"

        and:
        publication.groupId == "group2"
        publication.artifactId == "name2"
        publication.version == "version2"
    }

    def "packaging is taken from first added artifact without extension"() {
        when:
        def mavenArtifact = Mock(MavenArtifact)
        notationParser.parseNotation("artifact") >> mavenArtifact
        mavenArtifact.extension >> "ext"

        and:
        def publication = createPublication()
        publication.artifact "artifact"

        then:
        publication.pom.packaging == "ext"
    }

    def "packaging determines main artifact"() {
        when:
        def mavenArtifact = Mock(MavenTestArtifact) {
            shouldBePublished() >> true
        }
        notationParser.parseNotation("artifact") >> mavenArtifact
        mavenArtifact.extension >> "ext"
        def attachedMavenArtifact = Mock(MavenTestArtifact) {
            shouldBePublished() >> true
        }
        notationParser.parseNotation("attached") >> attachedMavenArtifact
        attachedMavenArtifact.extension >> "jar"

        and:
        def publication = createPublication()
        publication.artifact("artifact")
        publication.artifact("attached")
        publication.pom.packaging = "ext"

        then:
        publication.asNormalisedPublication().mainArtifact.extension == "ext"
        publication.pom.packaging == "ext"
    }

    def 'if there is only one artifact it is the main artifact even if packaging is different'() {
        when:
        def mavenArtifact = Mock(MavenTestArtifact) {
            shouldBePublished() >> true
        }
        notationParser.parseNotation("artifact") >> mavenArtifact
        mavenArtifact.extension >> "ext"

        and:
        def publication = createPublication()
        publication.artifact("artifact")
        publication.pom.packaging = "otherext"

        then:
        publication.asNormalisedPublication().mainArtifact.extension == "ext"
        publication.pom.packaging == "otherext"
    }

    def "empty publishableFiles and artifacts when no component is added"() {
        when:
        def publication = createPublication()

        then:
        publication.publishableArtifacts.files.files == [pomFile] as Set
        publication.artifacts.empty
        !publication.pom.dependencies.isPresent()
    }

    def "artifacts are taken from added component"() {
        given:
        def publication = createPublication()
        def artifact = Mock(PublishArtifact)
        artifact.file >> artifactFile
        artifact.classifier >> ""
        artifact.extension >> "jar"
        def publishArtifactDependencies = Mock(TaskDependency)

        def mavenArtifact = Mock(MavenArtifact)

        when:
        notationParser.parseNotation(artifact) >> mavenArtifact
        mavenArtifact.file >> artifactFile

        and:
        publication.from(componentWithArtifact(artifact))

        then:
        publication.publishableArtifacts.files.files == [pomFile, gradleMetadataFile, artifactFile] as Set
        publication.artifacts == [mavenArtifact] as Set
        publication.pom.dependencies.get().dependencies.empty

        when:
        def task = Mock(Task)
        mavenArtifact.buildDependencies >> publishArtifactDependencies
        publishArtifactDependencies.getDependencies(task) >> [task]

        then:
        publication.publishableArtifacts.files.buildDependencies.getDependencies(task).contains(task)
    }

    def "multiple usages of a component can provide the same artifact"() {
        given:
        def publication = createPublication()
        def artifact1 = Mock(PublishArtifact)
        artifact1.file >> artifactFile
        artifact1.classifier >> ""
        artifact1.extension >> "jar"
        def artifact2 = Mock(PublishArtifact)
        artifact2.file >> artifactFile
        artifact2.classifier >> ""
        artifact2.extension >> "jar"
        def variant1 = createVariant([artifact1], [], 'api')
        def variant2 = createVariant([artifact2], [], 'runtime')
        def component = Stub(SoftwareComponentInternal)
        component.usages >> [variant1, variant2]
        def mavenArtifact = Mock(MavenArtifact)
        mavenArtifact.file >> artifactFile
        notationParser.parseNotation(artifact1) >> mavenArtifact

        when:
        publication.from(component)

        then:
        publication.publishableArtifacts.files.files == [pomFile, gradleMetadataFile, artifactFile] as Set
        publication.artifacts == [mavenArtifact] as Set
    }

    def "adopts module dependency from added component"() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ExternalDependency)
        def artifact = Mock(DependencyArtifact) {
            getName() >> "dep-name"
            getClassifier() >> "artifact-classifier"
            getType() >> "artifact-type"
        }
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "dep-group"
        moduleDependency.name >> "dep-name"
        moduleDependency.version >> "dep-version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> true
        moduleDependency.attributes >> ImmutableAttributes.EMPTY
        moduleDependency.requestedCapabilities >> []

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().dependencies.size() == 1
        with(publication.pom.dependencies.get().dependencies.first()) {
            groupId == "dep-group"
            artifactId == "dep-name"
            version == "mapped-dep-version"
            type == "artifact-type"
            classifier == "artifact-classifier"
            scope == "runtime"
            excludeRules == [excludeRule] as Set
        }
    }

    def "filters self referencing module dependency from added component"() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ExternalDependency)
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "group"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.artifacts >> []
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> true
        moduleDependency.attributes >> ImmutableAttributes.EMPTY
        moduleDependency.requestedCapabilities >> []

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().dependencies.size() == 0
    }

    def "respects self referencing module dependency with custom artifact from added component"() {
        given:
        def publication = createPublication()
        def artifact = Mock(DependencyArtifact) {
            getName() >> "name"
            getClassifier() >> "artifact-classifier"
            getType() >> "artifact-type"
        }
        def moduleDependency = Mock(ExternalDependency)
        def excludeRule = Mock(ExcludeRule)

        when:
        artifact.classifier >> "other"
        moduleDependency.group >> "group"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> true
        moduleDependency.attributes >> ImmutableAttributes.EMPTY
        moduleDependency.requestedCapabilities >> []

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().dependencies.size() == 1
        with(publication.pom.dependencies.get().dependencies.asList().first()) {
            groupId == "group"
            artifactId == "name"
            version == "mapped-version"
            type == "artifact-type"
            classifier == "artifact-classifier"
            scope == "runtime"
            excludeRules == [excludeRule] as Set
        }
    }

    def "adopts non-transitive module dependency from added component"() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ExternalDependency)
        def artifact = Mock(DependencyArtifact) {
            getName() >> "dep-name"
            getClassifier() >> "artifact-classifier"
            getType() >> "artifact-type"
        }
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "dep-group"
        moduleDependency.name >> "dep-name"
        moduleDependency.version >> "dep-version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> false
        moduleDependency.attributes >> ImmutableAttributes.EMPTY
        moduleDependency.requestedCapabilities >> []

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().dependencies.size() == 1
        with(publication.pom.dependencies.get().dependencies.asList().first()) {
            groupId == "dep-group"
            artifactId == "dep-name"
            version == "mapped-dep-version"
            type == "artifact-type"
            classifier == "artifact-classifier"
            scope == "runtime"
            excludeRules != [excludeRule] as Set
            excludeRules.size() == 1
            excludeRules[0].group == '*'
            excludeRules[0].module == '*'
        }
    }

    def 'adopts platform in #scope declaration from added components'() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ExternalDependency)

        when:
        moduleDependency.group >> "plat-group"
        moduleDependency.name >> "plat-name"
        moduleDependency.version >> "plat-version"
        moduleDependency.artifacts >> []
        moduleDependency.excludeRules >> []
        moduleDependency.transitive >> true
        moduleDependency.attributes >> platformAttribute()
        moduleDependency.requestedCapabilities >> []

        and:
        publication.from(createComponent([], [moduleDependency], scope))

        then:
        publication.pom.dependencies.get().dependencyManagement.size() == 1
        with(publication.pom.dependencies.get().dependencyManagement.asList().first()) {
            groupId == "plat-group"
            artifactId == "plat-name"
            version == "mapped-plat-version"
            type == "pom"
            classifier == null
            getScope() == "import"
            excludeRules == [] as Set
        }

        where:
        scope << ['runtime', 'api']
    }

    def "maps project dependency to maven dependency"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependencyInternal) {
            getDependencyProject() >> Mock(Project)
            getIdentityPath() >> Mock(Path)
        }

        and:
        projectDependency.excludeRules >> []
        projectDependency.getAttributes() >> ImmutableAttributes.EMPTY
        projectDependency.requestedCapabilities >> []
        projectDependency.getArtifacts() >> []
        projectDependency.getGroup() >> "pub-group"
        projectDependency.getName() >> "pub-name"
        projectDependencyResolver.resolve(ModuleVersionIdentifier, projectDependency.identityPath) >> DefaultModuleVersionIdentifier.newId("pub-group", "pub-name", "pub-version")

        when:
        publication.from(componentWithDependency(projectDependency))

        then:
        publication.pom.dependencies.get().dependencies.size() == 1
        with(publication.pom.dependencies.get().dependencies.asList().first()) {
            groupId == "pub-group"
            artifactId == "pub-name"
            version == "pub-version"
            type == null
            classifier == null
            scope == "runtime"
        }
    }

    def "ignores self project dependency"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependencyInternal) {
            getIdentityPath() >> Mock(Path)
        }

        and:
        projectDependency.excludeRules >> []
        projectDependency.getAttributes() >> ImmutableAttributes.EMPTY
        projectDependency.requestedCapabilities >> []
        projectDependency.getArtifacts() >> []
        projectDependency.getGroup() >> "group"
        projectDependency.getName() >> "name"
        projectDependencyResolver.resolve(ModuleVersionIdentifier, projectDependency.identityPath) >> DefaultModuleVersionIdentifier.newId("group", "name", "version")

        when:
        publication.from(componentWithDependency(projectDependency))

        then:
        publication.pom.dependencies.get().dependencies.empty
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()

        when:
        publication.from(createComponent([], []))
        publication.from(Mock(SoftwareComponentInternal))

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Maven publication 'pub-name' cannot include multiple components"
    }

    def "attaches artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        def mavenArtifact = Mock(MavenArtifact)

        when:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> artifactFile

        and:
        publication.artifact notation

        then:
        publication.artifacts == [mavenArtifact] as Set
        publication.publishableArtifacts.files.files == [pomFile, artifactFile] as Set
    }

    def "attaches and configures artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        def mavenArtifact = Mock(MavenArtifact)

        when:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> artifactFile
        mavenArtifact.classifier >> null
        1 * mavenArtifact.setExtension('changed')
        _ * mavenArtifact.getExtension() >> 'changed'
        0 * mavenArtifact._

        and:
        publication.artifact(notation, new Action<MavenArtifact>() {
            void execute(MavenArtifact t) {
                t.extension = 'changed'
            }
        })

        then:
        publication.artifacts == [mavenArtifact] as Set
        publication.publishableArtifacts.files.files == [pomFile, artifactFile] as Set
    }

    def "can use setter to replace existing artifacts set"() {
        given:
        def publication = createPublication()
        def mavenArtifact1 = Mock(MavenArtifact)
        def mavenArtifact2 = Mock(MavenArtifact)

        when:
        publication.artifact "notation"

        then:
        notationParser.parseNotation("notation") >> Mock(MavenArtifact)

        when:
        publication.artifacts = ["notation1", "notation2"]

        then:
        notationParser.parseNotation("notation1") >> mavenArtifact1
        notationParser.parseNotation("notation2") >> mavenArtifact2

        and:
        publication.artifacts.size() == 2
        publication.artifacts == [mavenArtifact1, mavenArtifact2] as Set
    }

    def "resolving the publishable files does not throw if gradle metadata is not activated"() {
        given:
        def publication = createPublication()
        publication.setPomGenerator(createArtifactGenerator(pomFile))

        when:
        publication.publishableArtifacts.files.files

        then:
        noExceptionThrown()

        and:
        publication.publishableArtifacts.files.contains(pomFile)
    }

    def "Gradle metadata artifact is added for components with variants"() {
        given:
        def publication = createPublication()
        publication.from(Stub(SoftwareComponentInternal, additionalInterfaces: [ComponentWithVariants]))

        and:
        publication.publishableArtifacts.files.contains(gradleMetadataFile)
    }

    def "Gradle metadata artifact is not added for publications without a component"() {
        given:
        def publication = createPublication()

        and:
        publication.publishableArtifacts.files.isEmpty()
    }

    def "Gradle metadata artifact is added for components without variants"() {
        given:
        def publication = createPublication()
        publication.from(createComponent([], []))

        and:
        publication.publishableArtifacts.files.contains(gradleMetadataFile)
    }

    def createPublication() {
        def versionRangeMapper = Mock(VersionRangeMapper) {
            map(_) >> { "mapped-" + it[0] }
        }
        def objectFactory = TestUtil.createTestServices {
            it.add(PlatformSupport, DependencyManagementTestUtil.platformSupport())
            it.add(VersionRangeMapper, versionRangeMapper)
            it.add(ProjectDependencyPublicationResolver, projectDependencyResolver)
            it.add(ImmutableModuleIdentifierFactory, new DefaultImmutableModuleIdentifierFactory())
            it.add(AttributesSchemaInternal, EmptySchema.INSTANCE)
            it.add(ImmutableAttributesFactory, AttributeTestUtil.attributesFactory())
            it.add(AttributeDesugaring, new AttributeDesugaring(AttributeTestUtil.attributesFactory()))
        }.get(ObjectFactory)

        def versionMappingStrategy = Mock(VersionMappingStrategyInternal) {
            findStrategyForVariant(_) >> Mock(VariantVersionMappingStrategyInternal)
        }
        def publication = objectFactory.newInstance(DefaultMavenPublication,
            "pub-name",
            module,
            notationParser,
            versionMappingStrategy
        )
        publication.setPomGenerator(createArtifactGenerator(pomFile))
        publication.setModuleDescriptorGenerator(createArtifactGenerator(gradleMetadataFile))
        return publication
    }

    def createArtifactGenerator(File file) {
        return Stub(TaskProvider) {
            get() >> Stub(Task) {
                getOutputs() >> Stub(TaskOutputs) {
                    getFiles() >> Stub(FileCollection) {
                        getSingleFile() >> file
                    }
                }
            }
        }
    }

    def componentWithDependency(ModuleDependency dependency) {
        return createComponent([], [dependency])
    }

    def componentWithArtifact(def artifact) {
        return createComponent([artifact], [])
    }

    def createComponent(def artifacts, def dependencies) {
        return createComponent(artifacts, dependencies, 'runtime')
    }

    def createComponent(Collection<? extends PublishArtifact> artifacts, Collection<? extends ModuleDependency> dependencies, String scope) {
        def variant = createVariant(artifacts, dependencies, scope)
        def component = Stub(SoftwareComponentInternal) {
            getUsages() >> [variant]
        }
        return component
    }

    def createVariant(Collection<? extends PublishArtifact> artifacts, Collection<? extends ModuleDependency> dependencies, String scope) {
        new DefaultSoftwareComponentVariant(
            scope, ImmutableAttributes.EMPTY, artifacts as Set, dependencies as Set, [] as Set, [] as Set, [] as Set
        )
    }

    def otherPublication(String name, String group, String artifactId, String version) {
        def pub = Mock(PublicationInternal)
        pub.name >> name
        pub.coordinates >> new DefaultModuleVersionIdentifier(group, artifactId, version)
        return pub
    }

    def platformAttribute() {
        return AttributeTestUtil.attributesFactory().of(Category.CATEGORY_ATTRIBUTE, TestUtil.objectFactory().named(Category, Category.REGULAR_PLATFORM))
    }

    interface MavenTestArtifact extends MavenArtifact, PublicationArtifactInternal {
    }
}
