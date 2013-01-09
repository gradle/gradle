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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

public class DefaultMavenPublicationTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Instantiator instantiator = Mock()
    MavenProjectIdentity projectIdentity = Mock()
    MavenPomInternal mavenPom = Mock()
    File pomDir

    def "setup"() {
        pomDir = testDirectoryProvider.testDirectory
    }

    def "pom is constructed on init"() {
        when:
        def publication = new DefaultMavenPublication("pub-name", instantiator, projectIdentity, pomDir)

        then:
        instantiator.newInstance(DefaultMavenPom) >> mavenPom
        publication.pom == mavenPom
    }

    def "name and pomDir properties passed through"() {
        when:
        def publication = createPublication()

        then:
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
        DependencySet dependencySet = Mock()
        FileCollection files = Mock()
        publication.from(component)

        when:
        def publishableFiles = publication.publishableFiles

        then:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.files >> files

        and:
        publishableFiles == files

        when:
        def normalisedPublication = publication.asNormalisedPublication()

        then:
        component.artifacts >> publishArtifactSet
        component.runtimeDependencies >> dependencySet

        and:
        normalisedPublication.artifacts == publishArtifactSet
        normalisedPublication.runtimeDependencies == dependencySet
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()
        publication.from(Mock(SoftwareComponentInternal))

        when:
        publication.from(Mock(SoftwareComponentInternal))

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "A MavenPublication cannot include multiple components"
    }

    def createPublication() {
        instantiator.newInstance(DefaultMavenPom) >> mavenPom
        return new DefaultMavenPublication("pub-name", instantiator, projectIdentity, pomDir)
    }
}
