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
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.publish.PublicationArtifact
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultMavenPublicationTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def module = Mock(MavenProjectIdentity)
    NotationParser<Object, MavenArtifact> notationParser = Mock(NotationParser)
    def projectDependencyResolver = Mock(ProjectDependencyPublicationResolver)
    TestFile pomDir
    TestFile pomFile
    TestFile gradleMetadataFile
    File artifactFile

    def "setup"() {
        pomDir = testDirectoryProvider.testDirectory
        pomFile = pomDir.createFile("pom-file")
        gradleMetadataFile = pomDir.createFile("module-file")
        artifactFile = pomDir.createFile("artifact-file")
        artifactFile << "some content"
    }

    def "name and identity properties are passed through"() {
        when:
        module.artifactId >> "name"
        module.groupId >> "group"
        module.version >> "version"

        and:
        def publication = createPublication()

        then:
        publication.name == "pub-name"
        publication.mavenProjectIdentity.groupId == "group"
        publication.mavenProjectIdentity.artifactId == "name"
        publication.mavenProjectIdentity.version == "version"
    }

    def "changing publication coordinates does not effect those provided"() {
        when:
        module.artifactId >> "name"
        module.groupId >> "group"
        module.version >> "version"

        and:
        def publication = createPublication()

        and:
        publication.groupId = "group2"
        publication.artifactId = "name2"
        publication.version = "version2"

        then:
        module.groupId == "group"
        module.artifactId == "name"
        module.version == "version"

        and:
        publication.groupId == "group2"
        publication.artifactId == "name2"
        publication.version == "version2"

        and:
        publication.mavenProjectIdentity.groupId == "group2"
        publication.mavenProjectIdentity.artifactId == "name2"
        publication.mavenProjectIdentity.version == "version2"
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
        def mavenArtifact = Mock(MavenArtifact)
        notationParser.parseNotation("artifact") >> mavenArtifact
        mavenArtifact.extension >> "ext"
        def attachedMavenArtifact = Mock(MavenArtifact)
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
        def mavenArtifact = Mock(MavenArtifact)
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
        publication.publishableFiles.files == [pomFile, gradleMetadataFile] as Set
        publication.artifacts.empty
        publication.runtimeDependencies.empty
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
        publication.publishableFiles.files == [pomFile, gradleMetadataFile, artifactFile] as Set
        publication.artifacts == [mavenArtifact] as Set
        publication.runtimeDependencies.empty

        when:
        def task = Mock(Task)
        mavenArtifact.buildDependencies >> publishArtifactDependencies
        publishArtifactDependencies.getDependencies(task) >> [task]

        then:
        publication.publishableFiles.buildDependencies.getDependencies(task) == [task] as Set
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
        def usage1 = Stub(UsageContext)
        usage1.artifacts >> [artifact1]
        def usage2 = Stub(UsageContext)
        usage2.artifacts >> [artifact2]
        def component = Stub(SoftwareComponentInternal)
        component.usages >> [usage1, usage2]
        def mavenArtifact = Mock(MavenArtifact)
        mavenArtifact.file >> artifactFile
        notationParser.parseNotation(artifact1) >> mavenArtifact

        when:
        publication.from(component)

        then:
        publication.publishableFiles.files == [pomFile, gradleMetadataFile, artifactFile] as Set
        publication.artifacts == [mavenArtifact] as Set
    }

    def "adopts module dependency from added component"() {
        given:
        def publication = createPublication()
        def moduleDependency = Mock(ModuleDependency)
        def artifact = Mock(DependencyArtifact)
        def excludeRule = Mock(ExcludeRule)

        when:
        moduleDependency.group >> "group"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> true

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.runtimeDependencies.size() == 1
        with (publication.runtimeDependencies.asList().first()) {
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
        moduleDependency.group >> "group"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.artifacts >> [artifact]
        moduleDependency.excludeRules >> [excludeRule]
        moduleDependency.transitive >> false

        and:
        publication.from(componentWithDependency(moduleDependency))

        then:
        publication.runtimeDependencies.size() == 1
        with (publication.runtimeDependencies.asList().first()) {
                groupId == "group"
                artifactId == "name"
                version == "version"
                artifacts == [artifact]
                excludeRules != [excludeRule]
                excludeRules.size() == 1
                excludeRules[0].group == '*'
                excludeRules[0].module == '*'
        }
    }

    def "maps project dependency to maven dependency"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependency)

        and:
        projectDependency.excludeRules >> []
        projectDependencyResolver.resolve(ModuleVersionIdentifier, projectDependency) >> DefaultModuleVersionIdentifier.newId("pub-group", "pub-name", "pub-version")

        when:
        publication.from(componentWithDependency(projectDependency))

        then:
        publication.runtimeDependencies.size() == 1
        with (publication.runtimeDependencies.asList().first()) {
            groupId == "pub-group"
            artifactId == "pub-name"
            version == "pub-version"
            artifacts == []
        }
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
        publication.publishableFiles.files == [pomFile, gradleMetadataFile, artifactFile] as Set
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
        publication.publishableFiles.files == [pomFile, gradleMetadataFile, artifactFile] as Set
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
        def publication = new DefaultMavenPublication("pub-name", module, notationParser, DirectInstantiator.INSTANCE, projectDependencyResolver, TestFiles.fileCollectionFactory(), TestUtil.featurePreviews(), TestUtil.attributesFactory())
        publication.setPomArtifact(createPublicationArtifact(pomFile))

        when:
        publication.publishableFiles.files

        then:
        noExceptionThrown()

        and:
        publication.publishableFiles.contains(pomFile)
    }

    def createPublication() {
        def publication = new DefaultMavenPublication("pub-name", module, notationParser, DirectInstantiator.INSTANCE, projectDependencyResolver, TestFiles.fileCollectionFactory(), TestUtil.featurePreviews(), TestUtil.attributesFactory())
        publication.setPomArtifact(createPublicationArtifact(pomFile))
        publication.setGradleModuleMetadataArtifact(createPublicationArtifact(gradleMetadataFile))
        return publication
    }

    def createPublicationArtifact(File file) {
        return Mock(PublicationArtifact) {
            getFile() >> file
            getBuildDependencies() >> new DefaultTaskDependency()
        }
    }

    def createArtifact() {
        return Mock(MavenArtifact) {
            getFile() >> artifactFile
        }
    }

    def componentWithDependency(ModuleDependency dependency) {
        return createComponent([], [dependency])
    }

    def componentWithArtifact(def artifact) {
        return createComponent([artifact], [])
    }

    def createComponent(def artifacts, def dependencies) {
        def usage = Stub(UsageContext) {
            getName() >> "runtime"
            getArtifacts() >> artifacts
            getDependencies() >> dependencies
        }
        def component = Stub(SoftwareComponentInternal) {
            getUsages() >> [usage]
        }
        return component
    }

    def otherPublication(String name, String group, String artifactId, String version) {
        def pub = Mock(PublicationInternal)
        pub.name >> name
        pub.coordinates >> new DefaultModuleVersionIdentifier(group, artifactId, version)
        return pub
    }
}
