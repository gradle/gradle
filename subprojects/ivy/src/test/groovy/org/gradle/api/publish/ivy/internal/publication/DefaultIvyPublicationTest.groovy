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
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.Usage
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.DefaultPublicationContainer
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification

class DefaultIvyPublicationTest extends Specification {

    @Shared TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), new DirectInstantiator())
    def projectIdentity = Mock(IvyPublicationIdentity)
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
        publication.artifacts.empty
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.dependencies.empty
    }

    def "adopts configurations, artifacts and publishableFiles from added component"() {
        given:
        def publication = createPublication()

        def component = Mock(SoftwareComponentInternal)
        def usage1 = Mock(Usage)
        def artifact = Mock(PublishArtifact)
        def ivyArtifact = createArtifact()

        when:
        component.usages >> [usage1]
        usage1.name >> "runtime"
        usage1.artifacts >> [artifact]
        usage1.dependencies >> []

        notationParser.parseNotation(artifact) >> ivyArtifact
        1 * ivyArtifact.setConf("runtime")

        and:
        publication.from(component)

        then:
        publication.publishableFiles.files == [descriptorFile, artifactFile] as Set
        publication.artifacts == [ivyArtifact] as Set

        and:
        publication.configurations.size() == 2
        publication.configurations.runtime.extends == [] as Set
        publication.configurations."default".extends == ["runtime"] as Set
        
        publication.dependencies.empty
    }

    def "adopts module dependency from added component"() {
        given:
        def publication = createPublication()
        def component = Mock(SoftwareComponentInternal)
        def moduleDependency = Mock(ModuleDependency)
        def usage1 = Mock(Usage)
        def artifact = Mock(DependencyArtifact)

        when:
        component.usages >> [usage1]
        usage1.name >> "runtime"
        usage1.artifacts >> []
        usage1.dependencies >> [moduleDependency]

        moduleDependency.group >> "org"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.configuration >> "dep-configuration"
        moduleDependency.artifacts >> [artifact]

        and:
        publication.from(component)

        then:
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.artifacts.empty

        and:
        publication.dependencies.size() == 1
        def ivyDependency = publication.dependencies.asList().first()

        with (ivyDependency) {
            organisation == "org"
            module == "name"
            revision == "version"
            confMapping == "runtime->dep-configuration"
            artifacts == [artifact]
        }
    }

    def "adopts dependency on project with multiple publications"() {
        given:
        def publication = createPublication()
        def component = Mock(SoftwareComponentInternal)
        def usage1 = Mock(Usage)
        def projectDependency = Mock(ProjectDependency)
        def extensionContainer = Mock(ExtensionContainer)
        def publishingExtension = Mock(PublishingExtension)
        def publications = new DefaultPublicationContainer(new DirectInstantiator())
        publications.add(newIvyPub("ivyPub1", "pub-org", "pub-module", "pub-revision"))
        publications.add(newIvyPub("ivyPub2", "pub-org-2", "pub-module-2", "pub-revision-2"))

        when:
        component.usages >> [usage1]
        usage1.name >> "runtime"
        usage1.artifacts >> []
        usage1.dependencies >> [projectDependency]

        and:
        projectDependency.configuration >> "dep-configuration"
        projectDependency.artifacts >> []
        projectDependency.dependencyProject >> Stub(Project) {
            getExtensions() >> extensionContainer
        }
        extensionContainer.findByType(PublishingExtension) >> publishingExtension
        publishingExtension.publications >> publications

        and:
        publication.from(component)

        then:
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.artifacts.empty

        and:
        final deps = publication.dependencies.asList().sort({it.organisation})
        assert deps.size() == 2
        with (deps[0]) {
            organisation == "pub-org"
            module == "pub-module"
            revision == "pub-revision"
            confMapping == "runtime->dep-configuration"
            artifacts == []
        }
        with (deps[1]) {
            organisation == "pub-org-2"
            module == "pub-module-2"
            revision == "pub-revision-2"
            confMapping == "runtime->dep-configuration"
            artifacts == []
        }
    }

    def newIvyPub(String name, String org, String module, String revision) {
        def ivyPub = Mock(IvyPublicationInternal)
        ivyPub.name >> name
        ivyPub.organisation >> org
        ivyPub.module >> module
        ivyPub.revision >> revision
        return ivyPub
    }

    def "adopts dependency on project without a publication"() {
        given:
        def publication = createPublication()
        def component = Mock(SoftwareComponentInternal)
        def usage1 = Mock(Usage)
        def projectDependency = Mock(ProjectDependency)
        def extensionContainer = Mock(ExtensionContainer)

        when:
        component.usages >> [usage1]
        usage1.name >> "runtime"
        usage1.artifacts >> []
        usage1.dependencies >> [projectDependency]

        and:
        projectDependency.group >> "dep-group"
        projectDependency.name >> "dep-name-1"
        projectDependency.version >> "dep-version"
        projectDependency.configuration >> "dep-configuration"
        projectDependency.dependencyProject >> Stub(Project) {
            getExtensions() >> extensionContainer
            getName() >> "project-name"
        }
        projectDependency.artifacts >> []
        extensionContainer.findByType(PublishingExtension) >> null

        and:
        publication.from(component)

        then:
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.artifacts.empty

        and:
        publication.dependencies.size() == 1
        with (publication.dependencies.asList().first()) {
            organisation == "dep-group"
            module == "project-name"
            revision == "dep-version"
            confMapping == "runtime->dep-configuration"
            artifacts == []
        }
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
        usage.name >> "runtime"
        usage.artifacts >> publishArtifactSet
        publishArtifactSet.iterator() >> [].iterator()
        usage.dependencies >> dependencySet
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
        publication.artifacts == [ivyArtifact] as Set
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
        IvyArtifact artifact = Mock(IvyArtifact) {
            getFile() >> artifactFile
        }
        return artifact
    }
}
