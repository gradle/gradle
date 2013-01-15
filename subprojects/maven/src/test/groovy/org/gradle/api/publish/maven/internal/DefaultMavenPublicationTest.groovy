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
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.InvalidMavenPublicationException
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

public class DefaultMavenPublicationTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    MavenProjectIdentity projectIdentity = Mock()
    MavenPomInternal mavenPom = Mock()
    NotationParser<MavenArtifact> notationParser = Mock()
    File pomDir
    File artifactFile

    def "setup"() {
        pomDir = testDirectoryProvider.testDirectory
        artifactFile = new File(testDirectoryProvider.testDirectory, "artifact-file")
        artifactFile << "some content"
    }

    def "pom, name and pomDir properties passed through"() {
        when:
        def publication = createPublication()

        then:
        publication.pom == mavenPom
        publication.name == "pub-name"
        publication.pomDir == pomDir
    }

    def "empty publishableFiles and artifacts when no component is added"() {
        when:
        def publication = createPublication()

        then:
        publication.publishableFiles.isEmpty()
        publication.asNormalisedPublication().artifacts.empty
        publication.asNormalisedPublication().runtimeDependencies.empty
    }

    def "publishableFiles and artifacts when taken from added component"() {
        given:
        def publication = createPublication()
        SoftwareComponentInternal component = Mock()
        PublishArtifactSet publishArtifactSet = Mock()
        PublishArtifact artifact = Mock()
        DependencySet dependencySet = Mock()

        MavenArtifact mavenArtifact = Mock()

        when:
        publication.from(component)
        def normalisedPublication = publication.asNormalisedPublication()

        then:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [artifact].iterator()
        component.runtimeDependencies >> dependencySet

        notationParser.parseNotation(artifact) >> mavenArtifact
        mavenArtifact.file >> artifactFile

        and:
        normalisedPublication.artifacts == [mavenArtifact] as Set
        normalisedPublication.runtimeDependencies == dependencySet
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()
        SoftwareComponentInternal component = Mock()
        PublishArtifactSet publishArtifactSet = Mock()

        when:
        publication.from(component)

        then:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [].iterator()

        when:
        publication.from(Mock(SoftwareComponentInternal))

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "A MavenPublication cannot include multiple components"
    }

    def "attaches artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        MavenArtifact mavenArtifact = Mock()

        when:
        publication.artifact notation
        def normalisedPublication = publication.asNormalisedPublication()

        then:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> artifactFile

        and:
        normalisedPublication.artifacts == [mavenArtifact] as Set
    }

    def "attaches and configures artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        MavenArtifact mavenArtifact = Mock()

        when:
        publication.artifact(notation, new Action<MavenArtifact>() {
            void execute(MavenArtifact t) {
                t.extension = 'changed'
            }
        })
        def normalisedPublication = publication.asNormalisedPublication()

        then:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> artifactFile
        mavenArtifact.classifier >> null
        1 * mavenArtifact.setExtension('changed')
        0 * mavenArtifact._

        and:
        normalisedPublication.artifacts == [mavenArtifact] as Set
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

    def "cannot publish artifact with file that does not exist"() {
        def publication = createPublication()
        Object notation = new Object();
        MavenArtifact mavenArtifact = Mock()
        def nonExistentFile = new File(pomDir, 'does-not-exist')

        when:
        publication.artifact notation
        publication.asNormalisedPublication()

        then:
        notationParser.parseNotation(notation) >> mavenArtifact
        mavenArtifact.file >> nonExistentFile

        and:
        def t = thrown InvalidMavenPublicationException
        t.message == "Attempted to publish an artifact that does not exist: '${nonExistentFile}'"
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
        t.message == "Cannot determine main artifact: multiple artifacts found with empty classifier."
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
        t.message == "Cannot publish 2 artifacts with the identical extension 'ext1' and classifier 'classified'."
    }

    def createPublication() {
        return new DefaultMavenPublication("pub-name", mavenPom, projectIdentity, pomDir, notationParser)
    }
}
