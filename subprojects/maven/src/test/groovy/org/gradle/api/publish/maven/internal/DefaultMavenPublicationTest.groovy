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
import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification

public class DefaultMavenPublicationTest extends Specification {
    @Shared TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Module module = Mock()
    NotationParser<MavenArtifact> notationParser = Mock()
    TestFile pomDir
    TestFile pomFile
    File artifactFile

    def "setup"() {
        pomDir = testDirectoryProvider.testDirectory
        pomFile = pomDir.createFile("pom-file")
        artifactFile = pomDir.createFile("artifact-file")
        artifactFile << "some content"
    }

    def "name property is passed through"() {
        when:
        def publication = createPublication()

        then:
        publication.name == "pub-name"
    }

    def "project identity is adapted from module and main artifact"() {
        when:
        module.name >> "name"
        module.group >> "group"
        module.version >> "version"

        and:
        def publication = createPublication()
        def identity = publication.mavenProjectIdentity

        then:
        identity.groupId == "group"
        identity.artifactId == "name"
        identity.version == "version"
        identity.packaging == "pom"

        when:
        MavenArtifact mavenArtifact = Mock()
        notationParser.parseNotation("artifact") >> mavenArtifact
        mavenArtifact.extension >> "ext"

        and:
        publication.artifact "artifact"

        then:
        identity.packaging == "ext"
    }

    def "empty publishableFiles and artifacts when no component is added"() {
        when:
        def publication = createPublication()

        then:
        publication.publishableFiles.files == [pomFile] as Set
        publication.artifacts.empty
        publication.runtimeDependencies.empty
    }

    def "publishableFiles and artifacts when taken from added component"() {
        given:
        def publication = createPublication()
        SoftwareComponentInternal component = Mock()
        PublishArtifactSet publishArtifactSet = Mock()
        PublishArtifact artifact = Mock()
        TaskDependency publishArtifactDependencies = Mock()
        DependencySet dependencySet = Mock()

        MavenArtifact mavenArtifact = Mock()

        when:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [artifact].iterator()
        component.runtimeDependencies >> dependencySet
        dependencySet.iterator() >> [].iterator()

        notationParser.parseNotation(artifact) >> mavenArtifact
        mavenArtifact.file >> artifactFile

        and:
        publication.from(component)

        then:
        publication.publishableFiles.files == [pomFile, artifactFile] as Set
        publication.artifacts == [mavenArtifact] as Set
        publication.runtimeDependencies == dependencySet

        when:
        Task task = Mock()
        mavenArtifact.buildDependencies >> publishArtifactDependencies
        publishArtifactDependencies.getDependencies(task) >> [task]

        then:
        publication.publishableFiles.buildDependencies.getDependencies(task) == [task] as Set
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()
        SoftwareComponentInternal component = Mock()
        PublishArtifactSet publishArtifactSet = Mock()
        DependencySet dependencySet = Mock()

        when:
        publication.from(component)

        then:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [].iterator()
        component.runtimeDependencies >> dependencySet
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
        MavenArtifact mavenArtifact = Mock()

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
        MavenArtifact mavenArtifact = Mock()

        when:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> artifactFile
        mavenArtifact.classifier >> null
        1 * mavenArtifact.setExtension('changed')
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
        Object notation = new Object();
        MavenArtifact mavenArtifact1 = Mock()
        MavenArtifact mavenArtifact2 = Mock()

        when:
        publication.artifact "notation"

        then:
        notationParser.parseNotation(notation) >> Mock(MavenArtifact)

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
        MavenArtifact artifact = Mock() {
            getFile() >> artifactFile
        }
        return artifact
    }
}
