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
import org.gradle.api.publish.maven.InvalidMavenPublicationException
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

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

    def "mainArtifact is one without classifier"() {
        given:
        def publication = createPublication()
        MavenArtifact mavenArtifact1 = createArtifact()
        MavenArtifact mavenArtifact2 = createArtifact()
        MavenArtifact mavenArtifact3 = createArtifact()

        when:
        notationParser.parseNotation('artifact1') >> mavenArtifact1
        mavenArtifact1.classifier >> "source"
        notationParser.parseNotation('artifact2') >> mavenArtifact2
        mavenArtifact2.extension >> "ext"
        notationParser.parseNotation('artifact3') >> mavenArtifact3
        mavenArtifact3.classifier >> "docs"

        and:
        publication.artifact 'artifact1'
        publication.artifact 'artifact2'
        publication.artifact 'artifact3'

        then:
        publication.mavenProjectIdentity.packaging == "ext"
        publication.asNormalisedPublication().mainArtifact == mavenArtifact2
        publication.asNormalisedPublication().additionalArtifacts == [mavenArtifact1, mavenArtifact3] as Set
    }

    def "cannot publish with ambiguous mainArtifact"() {
        given:
        def publication = createPublication()
        MavenArtifact artifact1 = Stub() {
            getExtension() >> "ext1"
        }
        MavenArtifact artifact2 = Stub() {
            getExtension() >> "ext2"
        }

        when:
        publication.artifact "art1"
        publication.artifact "art2"

        then:
        notationParser.parseNotation("art1") >> artifact1
        notationParser.parseNotation("art2") >> artifact2

        when:
        publication.asNormalisedPublication()

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Cannot determine main artifact for maven publication 'pub-name': multiple artifacts found with empty classifier."
    }


    def "cannot publish with duplicate artifacts"() {
        given:
        def publication = createPublication()
        MavenArtifact artifact1 = Stub() {
            getExtension() >> "ext1"
            getClassifier() >> "classified"
        }
        MavenArtifact artifact2 = Stub() {
            getExtension() >> "ext1"
            getClassifier() >> "classified"
        }

        when:
        publication.artifact "art1"
        publication.artifact "art2"

        then:
        notationParser.parseNotation("art1") >> artifact1
        notationParser.parseNotation("art2") >> artifact2

        when:
        publication.asNormalisedPublication()

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Cannot publish maven publication 'pub-name': multiple artifacts with the identical extension 'ext1' and classifier 'classified'."
    }

    @Unroll
    def "cannot publish with file that #message"() {
        def publication = createPublication()
        Object notation = new Object();
        MavenArtifact mavenArtifact = Mock()

        when:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> theFile

        and:
        publication.artifact notation
        publication.asNormalisedPublication()

        then:
        def t = thrown InvalidMavenPublicationException
        t.message == "Cannot publish maven publication 'pub-name': artifact file ${message}: '${theFile}'"

        where:
        theFile                                                         | message
        new File(testDirectoryProvider.testDirectory, 'does-not-exist') | 'does not exist'
        testDirectoryProvider.testDirectory.createDir('sub_directory')  | 'is a directory'
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
