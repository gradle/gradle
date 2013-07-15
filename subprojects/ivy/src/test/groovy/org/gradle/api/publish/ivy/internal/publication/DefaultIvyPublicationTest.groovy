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
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.Usage
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.DefaultPublicationContainer
import org.gradle.api.publish.internal.PublicationInternal
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

    def "status property is defaults to 'integration'"() {
        when:
        def publication = createPublication()

        then:
        publication.descriptor.status == "integration"
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
        def artifact = Mock(PublishArtifact)
        def ivyArtifact = createArtifact()

        when:
        notationParser.parseNotation(artifact) >> ivyArtifact
        1 * ivyArtifact.setConf("runtime")

        and:
        publication.from(componentWithArtifact(artifact))

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
        def moduleDependency = Mock(ModuleDependency)
        def artifact = Mock(DependencyArtifact)

        when:
        moduleDependency.group >> "org"
        moduleDependency.name >> "name"
        moduleDependency.version >> "version"
        moduleDependency.configuration >> "dep-configuration"
        moduleDependency.artifacts >> [artifact]

        and:
        publication.from(componentWithDependency(moduleDependency))

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

    def "adopts dependency on project with single publications"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependency)
        def extensionContainer = Mock(ExtensionContainer)
        def publishingExtension = Mock(PublishingExtension)
        def publications = new DefaultPublicationContainer(new DirectInstantiator())
        publications.add(otherPublication("ivyPub1", "pub-org", "pub-module", "pub-revision"))

        when:
        projectDependency.configuration >> "dep-configuration"
        projectDependency.artifacts >> []
        projectDependency.dependencyProject >> Stub(Project) {
            getExtensions() >> extensionContainer
        }
        extensionContainer.findByType(PublishingExtension) >> publishingExtension
        publishingExtension.publications >> publications

        and:
        publication.from(componentWithDependency(projectDependency))

        then:
        publication.publishableFiles.files == [descriptorFile] as Set
        publication.artifacts.empty

        and:
        publication.dependencies.size() == 1
        def ivyDependency = publication.dependencies.asList().first()

        with (ivyDependency) {
            organisation == "pub-org"
            module == "pub-module"
            revision == "pub-revision"
            confMapping == "runtime->dep-configuration"
            artifacts == []
        }
    }

    def "fails to resolve dependency on project with multiple publications"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependency)
        def extensionContainer = Mock(ExtensionContainer)
        def publishingExtension = Mock(PublishingExtension)
        def publications = new DefaultPublicationContainer(new DirectInstantiator())
        publications.add(otherPublication("ivyPub1", "pub-org", "pub-module", "pub-revision"))
        publications.add(otherPublication("ivyPub2", "pub-org-2", "pub-module-2", "pub-revision-2"))

        when:
        projectDependency.configuration >> "dep-configuration"
        projectDependency.artifacts >> []
        projectDependency.dependencyProject >> Stub(Project) {
            getExtensions() >> extensionContainer
        }
        extensionContainer.findByType(PublishingExtension) >> publishingExtension
        publishingExtension.publications >> publications

        and:
        publication.from(componentWithDependency(projectDependency))

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Publishing is not yet able to resolve a dependency on a project with multiple different publications."
    }

    def "adopts dependency on project without a publication"() {
        given:
        def publication = createPublication()
        def projectDependency = Mock(ProjectDependency)
        def extensionContainer = Mock(ExtensionContainer)

        when:
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
        publication.from(componentWithDependency(projectDependency))

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

        when:
        publication.from(createComponent([], []))
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

    def componentWithDependency(ModuleDependency dependency) {
        return createComponent([], [dependency])
    }

    def componentWithArtifact(def artifact) {
        return createComponent([artifact], [])
    }

    def createComponent(def artifacts, def dependencies) {
        def usage = Stub(Usage) {
            getName() >> "runtime"
            getArtifacts() >> artifacts
            getDependencies() >> dependencies
        }
        def component = Stub(SoftwareComponentInternal) {
            getUsages() >> [usage]
        }
        return component
    }

    def otherPublication(String name, String org, String module, String revision) {
        def pub = Mock(PublicationInternal)
        pub.name >> name
        pub.coordinates >> new DefaultModuleVersionIdentifier(org, module, revision)
        return pub
    }
}
