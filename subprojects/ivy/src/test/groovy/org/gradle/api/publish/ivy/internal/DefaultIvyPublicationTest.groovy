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

package org.gradle.api.publish.ivy.internal
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class DefaultIvyPublicationTest extends Specification {

    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Module module = Mock()
    NotationParser<IvyArtifact> notationParser = Mock()
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

    def "project identity is taken directly adapted from project module"() {
        when:
        module.name >> "name"
        module.group >> "group"
        module.version >> "version"

        and:
        def publication = createPublication()
        def descriptorModule = publication.descriptor.module

        then:
        descriptorModule.group == "group"
        descriptorModule.name == "name"
        descriptorModule.version == "version"
    }

    def "empty publishableFiles and artifacts when no component is added"() {
        when:
        def publication = createPublication()

        then:
        artifactsOf(publication).empty
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.runtimeDependencies.empty
    }

    def "publishableFiles and artifacts when taken from added component"() {
        given:
        def publication = createPublication()

        SoftwareComponentInternal component = Mock()
        PublishArtifactSet publishArtifactSet = Mock()
        PublishArtifact artifact = Mock()
        DependencySet dependencySet = Mock()
        IvyArtifact ivyArtifact = createArtifact()

        when:
        component.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [artifact].iterator()
        component.runtimeDependencies >> dependencySet

        notationParser.parseNotation(artifact) >> ivyArtifact

        and:
        publication.from(component)

        then:
        artifactsOf(publication) == [ivyArtifact] as Set
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
        publication.runtimeDependencies == dependencySet
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
        e.message == "An IvyPublication cannot include multiple components"
    }

    def "attaches artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        IvyArtifact ivyArtifact = createArtifact()

        when:
        notationParser.parseNotation(notation) >> ivyArtifact

        and:
        publication.artifact notation

        then:
        artifactsOf(publication) == [ivyArtifact] as Set
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
    }

    def "attaches and configures artifacts parsed by notation parser"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        IvyArtifact ivyArtifact = createArtifact()

        when:
        notationParser.parseNotation(notation) >> ivyArtifact
        1 * ivyArtifact.setExtension('changed')
        0 * ivyArtifact._

        and:
        publication.artifact(notation, new Action<IvyArtifact>() {
            void execute(IvyArtifact t) {
                t.extension = 'changed'
            }
        })

        then:
        artifactsOf(publication) == [ivyArtifact] as Set
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
    }

    def "can use setter to replace existing artifacts set"() {
        given:
        def publication = createPublication()
        Object notation = new Object();
        IvyArtifact ivyArtifact1 = createArtifact()
        IvyArtifact ivyArtifact2 = createArtifact()

        when:
        publication.artifact "notation"

        then:
        notationParser.parseNotation(notation) >> Mock(IvyArtifact)

        when:
        publication.artifacts = ["notation1", "notation2"]

        then:
        notationParser.parseNotation("notation1") >> ivyArtifact1
        notationParser.parseNotation("notation2") >> ivyArtifact2

        and:
        artifactsOf(publication) == [ivyArtifact1, ivyArtifact2] as Set
    }

    def "getting normalised publication will fail with file that does not exist"() {
        def publication = createPublication()
        Object notation = new Object();
        IvyArtifact ivyArtifact = Mock()
        def nonExistentFile = new File(testDirectoryProvider.testDirectory, 'does-not-exist')

        when:
        publication.artifact notation
        publication.asNormalisedPublication()

        then:
        notationParser.parseNotation(notation) >> ivyArtifact
        ivyArtifact.file >> nonExistentFile

        and:
        def t = thrown InvalidUserDataException
        t.message == "Attempted to publish an artifact that does not exist: '${nonExistentFile}'"
    }

    def createPublication() {
        def publication = new DefaultIvyPublication("pub-name", new DirectInstantiator(), module, notationParser)
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
