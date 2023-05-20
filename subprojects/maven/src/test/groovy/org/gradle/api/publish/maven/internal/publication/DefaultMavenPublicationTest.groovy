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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Category
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.publish.internal.PublicationArtifactInternal
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.publisher.MutableMavenProjectIdentity
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultMavenPublicationTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    MutableMavenProjectIdentity module
    NotationParser<Object, MavenArtifact> notationParser = Mock(NotationParser)
    def projectDependencyResolver = Mock(ProjectDependencyPublicationResolver)
    TestFile pomDir
    TestFile pomFile
    TestFile gradleMetadataFile
    File artifactFile
    def featurePreviews = TestUtil.featurePreviews()

    def "setup"() {
        module = new WritableMavenProjectIdentity(TestUtil.objectFactory())
        module.groupId.set("group")
        module.artifactId.set("name")
        module.version.set("version")
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
        publication.mavenProjectIdentity.groupId.get() == "group"
        publication.mavenProjectIdentity.artifactId.get() == "name"
        publication.mavenProjectIdentity.version.get() == "version"
    }

    def "publication coordinates are live"() {
        when:
        def publication = createPublication()

        and:
        publication.groupId = "group2"
        publication.artifactId = "name2"
        publication.version = "version2"

        then:
        module.groupId.get() == "group2"
        module.artifactId.get() == "name2"
        module.version.get() == "version2"

        and:
        publication.groupId == "group2"
        publication.artifactId == "name2"
        publication.version == "version2"

        and:
        publication.mavenProjectIdentity.groupId.get() == "group2"
        publication.mavenProjectIdentity.artifactId.get() == "name2"
        publication.mavenProjectIdentity.version.get() == "version2"
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
        publication.pom.dependencies.get().runtimeDependencies.empty
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
        publication.pom.dependencies.get().runtimeDependencies.empty

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
        def variant1 = Stub(UsageContext) { getName() >> 'api' }
        variant1.artifacts >> [artifact1]
        def variant2 = Stub(UsageContext) { getName() >> 'runtime' }
        variant2.artifacts >> [artifact2]
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
        def moduleDependency = Mock(ModuleDependency)
        def artifact = Mock(DependencyArtifact)
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "dep-group"
        moduleDependency.name >> "dep-name"
        moduleDependency.version >> "dep-version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> true
        moduleDependency.attributes >> ImmutableAttributes.EMPTY

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().runtimeDependencies.size() == 1
        with(publication.pom.dependencies.get().runtimeDependencies.asList().first()) {
            groupId == "dep-group"
            artifactId == "dep-name"
            version == "dep-version"
            artifacts == [artifact]
            excludeRules == [excludeRule]
        }
    }

    def "filters self referencing module dependency from added component"() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ModuleDependency)
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "group"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.artifacts >> []
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> true
        moduleDependency.attributes >> ImmutableAttributes.EMPTY

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().runtimeDependencies.size() == 0
    }

    def "respects self referencing module dependency with custom artifact from added component"() {
        given:
        def publication = createPublication()
        def artifact = Mock(DependencyArtifact)
        def moduleDependency = Mock(ModuleDependency)
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

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().runtimeDependencies.size() == 1
        with(publication.pom.dependencies.get().runtimeDependencies.asList().first()) {
            groupId == "group"
            artifactId == "name"
            version == "version"
            artifacts == [artifact]
            excludeRules == [excludeRule]
        }
    }

    def "adopts non-transitive module dependency from added component"() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ModuleDependency)
        def artifact = Mock(DependencyArtifact)
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "dep-group"
        moduleDependency.name >> "dep-name"
        moduleDependency.version >> "dep-version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> false
        moduleDependency.attributes >> ImmutableAttributes.EMPTY

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.pom.dependencies.get().runtimeDependencies.size() == 1
        with(publication.pom.dependencies.get().runtimeDependencies.asList().first()) {
            groupId == "dep-group"
            artifactId == "dep-name"
            version == "dep-version"
            artifacts == [artifact]
            excludeRules != [excludeRule]
            excludeRules.size() == 1
            excludeRules[0].group == '*'
            excludeRules[0].module == '*'
        }
    }

    def 'adopts platform in #scope declaration from added components'() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ModuleDependency)

        when:
        moduleDependency.group >> "plat-group"
        moduleDependency.name >> "plat-name"
        moduleDependency.version >> "plat-version"
        moduleDependency.artifacts >> []
        moduleDependency.excludeRules >> []
        moduleDependency.transitive >> true
        moduleDependency.attributes >> platformAttribute()

        and:
        publication.from(createComponent([], [moduleDependency], scope))

        then:
        publication.pom.dependencies.get().importDependencyManagement.size() == 1
        with(publication.pom.dependencies.get().importDependencyManagement.asList().first()) {
            groupId == "plat-group"
            artifactId == "plat-name"
            version == "plat-version"
            artifacts == []
            excludeRules == []
        }

        where:
        scope << ['runtime', 'api']
    }

    def "maps project dependency to maven dependency"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependency) {
            getDependencyProject() >> Mock(Project)
        }

        and:
        projectDependency.excludeRules >> []
        projectDependency.getAttributes() >> ImmutableAttributes.EMPTY
        projectDependency.getArtifacts() >> []
        projectDependency.getGroup() >> "pub-group"
        projectDependency.getName() >> "pub-name"
        projectDependencyResolver.resolve(ModuleVersionIdentifier, projectDependency) >> DefaultModuleVersionIdentifier.newId("pub-group", "pub-name", "pub-version")

        when:
        publication.from(componentWithDependency(projectDependency))

        then:
        publication.pom.dependencies.get().runtimeDependencies.size() == 1
        with(publication.pom.dependencies.get().runtimeDependencies.asList().first()) {
            groupId == "pub-group"
            artifactId == "pub-name"
            version == "pub-version"
            artifacts == []
        }
    }

    def "ignores self project dependency"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependency)

        and:
        projectDependency.excludeRules >> []
        projectDependency.getAttributes() >> ImmutableAttributes.EMPTY
        projectDependency.getArtifacts() >> []
        projectDependency.getGroup() >> "group"
        projectDependency.getName() >> "name"
        projectDependencyResolver.resolve(ModuleVersionIdentifier, projectDependency) >> DefaultModuleVersionIdentifier.newId("group", "name", "version")

        when:
        publication.from(componentWithDependency(projectDependency))

        then:
        publication.pom.dependencies.get().runtimeDependencies.size() == 0
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()

        when:
        publication.from(createComponent([], []))
        publication.from(Mock(SoftwareComponentInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == "The value for property 'component' is final and cannot be changed any further."
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
        def instantiator = TestUtil.instantiatorFactory().decorateLenient()
        def objectFactory = TestUtil.objectFactory()
        def publication = objectFactory.newInstance(DefaultMavenPublication.class, "pub-name", module, notationParser, instantiator, objectFactory, projectDependencyResolver, TestFiles.fileCollectionFactory(),
            AttributeTestUtil.attributesFactory(), CollectionCallbackActionDecorator.NOOP, Mock(VersionMappingStrategyInternal), DependencyManagementTestUtil.platformSupport(),
            Mock(DocumentationRegistry), TestFiles.taskDependencyFactory())
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

    def createComponent(def artifacts, def dependencies, def scope) {
        def variant = Stub(UsageContext) {
            getName() >> scope
            getArtifacts() >> artifacts
            getDependencies() >> dependencies
        }
        def component = Stub(SoftwareComponentInternal) {
            getUsages() >> [variant]
        }
        return component
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
