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
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

public class DefaultMavenPublicationTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    MavenProjectIdentity projectIdentity = Mock()
    MavenPomInternal mavenPom = Mock()
    NotationParser<MavenArtifact> notationParser = Mock()
    File pomDir

    def "setup"() {
        pomDir = testDirectoryProvider.testDirectory
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

        then:
        notationParser.parseNotation(notation) >> mavenArtifact

        and:
        publication.asNormalisedPublication().artifacts == [mavenArtifact] as Set
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

        then:
        notationParser.parseNotation(notation) >> mavenArtifact
        1 * mavenArtifact.setExtension('changed')
        0 * mavenArtifact._

        and:
        publication.asNormalisedPublication().artifacts == [mavenArtifact] as Set
    }

    def createPublication() {
        return new DefaultMavenPublication("pub-name", mavenPom, projectIdentity, pomDir, notationParser)
    }
}
