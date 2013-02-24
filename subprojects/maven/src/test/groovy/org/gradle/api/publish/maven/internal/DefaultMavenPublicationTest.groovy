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
package org.gradle.api.publish.maven.internal
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.Usage
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification

public class DefaultMavenPublicationTest extends Specification {
    @Shared TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    def module = Mock(MavenProjectIdentity)
    NotationParser<MavenArtifact> notationParser = Mock(NotationParser)
    TestFile pomDir
    TestFile pomFile
    File artifactFile

    def "setup"() {
        pomDir = testDirectoryProvider.testDirectory
        pomFile = pomDir.createFile("pom-file")
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
        publication.mavenProjectIdentity == module
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

    def "empty publishableFiles and artifacts when no component is added"() {
        when:
        def publication = createPublication()

        then:
        publication.publishableFiles.files == [pomFile] as Set
        publication.artifacts.empty
        publication.runtimeDependencies.empty
    }

    def "artifacts and dependencies are taken from added component"() {
        given:
        def publication = createPublication()
        def component = Mock(SoftwareComponentInternal)
        def usage = Mock(Usage)
        def artifact = Mock(PublishArtifact)
        def dependency = Mock(ModuleDependency)
        def publishArtifactDependencies = Mock(TaskDependency)

        def mavenArtifact = Mock(MavenArtifact)

        when:
        component.usages >> [usage]
        usage.artifacts >> [artifact]
        usage.dependencies >> [dependency]

        notationParser.parseNotation(artifact) >> mavenArtifact
        mavenArtifact.file >> artifactFile

        and:
        publication.from(component)

        then:
        publication.publishableFiles.files == [pomFile, artifactFile] as Set
        publication.artifacts == [mavenArtifact] as Set
        publication.runtimeDependencies == [dependency] as Set

        when:
        def task = Mock(Task)
        mavenArtifact.buildDependencies >> publishArtifactDependencies
        publishArtifactDependencies.getDependencies(task) >> [task]

        then:
        publication.publishableFiles.buildDependencies.getDependencies(task) == [task] as Set
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()
        def component = Mock(SoftwareComponentInternal)
        def usage = Mock(Usage)
        def publishArtifactSet = Mock(PublishArtifactSet)
        def dependencySet = Mock(DependencySet)

        when:
        publication.from(component)

        then:
        component.usages >> [usage]
        usage.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [].iterator()
        usage.dependencies >> dependencySet
        dependencySet.iterator() >> [].iterator()

        when:
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
        publication.publishableFiles.files == [pomFile, artifactFile] as Set
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
        publication.publishableFiles.files == [pomFile, artifactFile] as Set
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

    def createPublication() {
        def publication = new DefaultMavenPublication("pub-name", module, notationParser, new DirectInstantiator())
        publication.setPomFile(new SimpleFileCollection(pomFile))
        return publication;
    }

    def createArtifact() {
        def artifact = Mock(MavenArtifact) {
            getFile() >> artifactFile
        }
        return artifact
    }
}
