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

package org.gradle.api.publish.ivy.internal.publication
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.publisher.IvyProjectIdentity
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification

class DefaultIvyPublicationTest extends Specification {

    @Shared TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), new DirectInstantiator())
    def projectIdentity = Mock(IvyProjectIdentity)
    def notationParser = Mock(NotationParser)
    File descriptorFile
    File artifactFile

    def "setup"() {
        descriptorFile = new File(testDirectoryProvider.testDirectory, "pom-file")
        artifactFile = new File(testDirectoryProvider.testDirectory, "artifact-file")
        artifactFile << "some content"
    }

    def "name property is passed through"() {
        when:
        def publication = createPublication()

        then:
        publication.name == "pub-name"
    }

    def "empty publishableFiles and artifacts when no component is added"() {
        when:
        def publication = createPublication()

        then:
        artifactsOf(publication).empty
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.runtimeDependencies.empty
    }

    def "adopts dependencies, configurations, artifacts and publishableFiles from added component"() {
        given:
        def publication = createPublication()

        def component = Mock(SoftwareComponentInternal)
        def publishArtifactSet = Mock(PublishArtifactSet)
        def artifact = Mock(PublishArtifact)
        def dependencySet = Mock(DependencySet)
        def ivyArtifact = createArtifact()

        when:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [artifact].iterator()
        component.runtimeDependencies >> dependencySet
        dependencySet.iterator() >> [].iterator()

        notationParser.parseNotation(artifact) >> ivyArtifact

        and:
        publication.from(component)

        then:
        artifactsOf(publication) == [ivyArtifact] as Set
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
        publication.runtimeDependencies == dependencySet

        and:
        publication.configurations.size() == 2
        publication.configurations.runtime.extends == [] as Set
        publication.configurations."default".extends == ["runtime"] as Set

        publication.artifacts == [ivyArtifact] as Set
    }

    def "cannot add multiple components"() {
        given:
        def publication = createPublication()
        def component = Mock(SoftwareComponentInternal)
        def publishArtifactSet = Mock(PublishArtifactSet)
        def dependencySet = Mock(DependencySet)

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
        e.message == "Ivy publication 'pub-name' cannot include multiple components"
    }

    def "creates configuration on first access"() {
        def publication = createPublication()

        when:
        publication.configurations {
            newConfiguration {}
        };

        then:
        publication.configurations.size() == 1
        publication.configurations.getByName("newConfiguration").name == "newConfiguration"
    }

    def "attaches artifacts parsed by notation parser to configuration"() {
        given:
        def publication = createPublication()
        def notation = new Object();
        def ivyArtifact = createArtifact()

        when:
        notationParser.parseNotation(notation) >> ivyArtifact

        and:
        publication.artifact notation

        then:
        artifactsOf(publication) == [ivyArtifact] as Set
        publication.artifacts == [ivyArtifact] as Set
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
    }

    def "attaches and configures artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        def notation = new Object();
        def ivyArtifact = createArtifact()

        when:
        notationParser.parseNotation(notation) >> ivyArtifact
        1 * ivyArtifact.setExtension('changed')
        0 * ivyArtifact._

        and:
        publication.artifact(notation) {
            extension = 'changed'
        }

        then:
        artifactsOf(publication) == [ivyArtifact] as Set
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
    }

    def "can use setter to replace existing artifacts set on configuration"() {
        given:
        def publication = createPublication()
        def ivyArtifact1 = createArtifact()
        def ivyArtifact2 = createArtifact()

        when:
        publication.artifact "notation"

        then:
        notationParser.parseNotation("notation") >> Mock(IvyArtifact)

        when:
        publication.artifacts = ["notation1", "notation2"]

        then:
        notationParser.parseNotation("notation1") >> ivyArtifact1
        notationParser.parseNotation("notation2") >> ivyArtifact2

        and:
        publication.artifacts == [ivyArtifact1, ivyArtifact2] as Set
    }

    def createPublication() {
        def publication = instantiator.newInstance(DefaultIvyPublication, "pub-name", instantiator, projectIdentity, notationParser)
        publication.setDescriptorFile(new SimpleFileCollection(descriptorFile))
        return publication;
    }

    def createArtifact() {
        IvyArtifact artifact = Mock() {
            getFile() >> artifactFile
        }
        return artifact
    }

    private static Set<IvyArtifact> artifactsOf(DefaultIvyPublication publication) {
        publication.asNormalisedPublication().artifacts
    }

}
