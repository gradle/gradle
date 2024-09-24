/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.api.publish.maven.internal.artifact

import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import spock.lang.Issue

import java.nio.file.Paths

class MavenArtifactNotationParserFactoryTest extends AbstractProjectBuilderSpec {
    Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def task = Mock(Task)
    def dependencies = ImmutableSet.of(task)
    def taskDependency = TestFiles.taskDependencyFactory().configurableDependency(dependencies)
    def fileNotationParser = Mock(NotationParser)
    def publishArtifact = Stub(PublishArtifact) {
        getExtension() >> 'extension'
        getClassifier() >> 'classifier'
        getFile() >> new File('foo')
        getBuildDependencies() >> taskDependency
    }

    NotationParser<Object, MavenArtifact> parser

    def "setup"() {
        def fileResolver = Stub(FileResolver) {
            asNotationParser() >> fileNotationParser
            resolve(_) >> { Object path -> fileNotationParser.parseNotation(path) }
        }
        parser = new MavenArtifactNotationParserFactory(instantiator, fileResolver, TestFiles.taskDependencyFactory(), TestUtil.objectFactory(), TestUtil.providerFactory()).create()
    }

    def "directly returns MavenArtifact input"() {
        when:
        MavenArtifact mavenArtifact = Mock()
        def output = parser.parseNotation(mavenArtifact)

        then:
        output == mavenArtifact
    }

    def "creates MavenArtifact for PublishArtifact"() {
        when:
        def mavenArtifact = parser.parseNotation(publishArtifact)

        then:
        mavenArtifact.getExtension().get() == publishArtifact.extension
        mavenArtifact.getClassifier().get() == publishArtifact.classifier
        mavenArtifact.file.get().asFile == publishArtifact.file

        and:
        mavenArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates MavenArtifact for source map notation"() {
        when:
        MavenArtifact mavenArtifact = parser.parseNotation(source: publishArtifact)

        then:
        mavenArtifact.getExtension().get() == publishArtifact.extension
        mavenArtifact.getClassifier().get() == publishArtifact.classifier
        mavenArtifact.file.get().asFile == publishArtifact.file

        and:
        mavenArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates and configures MavenArtifact for source map notation"() {
        when:
        MavenArtifact mavenArtifact = parser.parseNotation(source: publishArtifact, extension: "ext", classifier: "classy")

        then:
        mavenArtifact.file.get().asFile == publishArtifact.file
        mavenArtifact.getExtension().get() == "ext"
        mavenArtifact.getClassifier().get() == "classy"

        and:
        mavenArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates MavenArtifact for source map notation with file"() {
        given:
        File file = new File('some-file-1.2-classifier.zip')

        when:
        MavenArtifact mavenArtifact = parser.parseNotation(source: 'some-file')

        then:
        fileNotationParser.parseNotation('some-file') >> file

        and:
        mavenArtifact.getExtension().get() == "zip"
        mavenArtifact.file.get().asFile == file
        !mavenArtifact.getClassifier().isPresent()
    }

    def "creates MavenArtifact for ArchivePublishArtifact"() {
        when:
        def rootProject = TestUtil.createRootProject(temporaryFolder.testDirectory)
        def archive = rootProject.task('foo', type: Zip, {})
        archive.archiveBaseName.set("baseName")
        archive.destinationDirectory.set(temporaryFolder.testDirectory)
        archive.archiveExtension.set(archiveExtension)
        archive.archiveClassifier.set(archiveClassifier)

        MavenArtifact mavenArtifact = parser.parseNotation(archive)

        then:
        mavenArtifact.extension.getOrNull() == artifactExtension
        mavenArtifact.classifier.getOrNull() == Strings.emptyToNull(artifactClassifier)
        mavenArtifact.file.get().asFile == archive.archiveFile.get().asFile
        mavenArtifact.buildDependencies.getDependencies(null) == [archive] as Set

        where:
        archiveClassifier | artifactClassifier | archiveExtension | artifactExtension
        "classifier"      | "classifier"       | "extension"      | "extension"
        null              | null               | null             | null
        "" | "" | "" | ""
    }

    def "creates MavenArtifact for file notation"() {
        when:
        File file = new File(fileName)
        fileNotationParser.parseNotation('some-file') >> file

        and:
        MavenArtifact mavenArtifact = parser.parseNotation('some-file')

        then:
        mavenArtifact.extension.get() == extension
        mavenArtifact.file.get().asFile == file
        !mavenArtifact.classifier.isPresent()

        where:
        fileName                       | extension
        "some-file"                    | ""
        "some-file.zip"                | "zip"
        "some-file-1.2-classifier.zip" | "zip"
    }

    def "creates lazy MavenArtifact for Provider<AbstractArchiveTask> notation"() {
        def task = Mock(AbstractArchiveTask)
        def taskProvider = Mock(TestTaskProvider)
        def fileProvider = Mock(Provider)
        def file = Mock(RegularFile)

        when:
        def artifact = parser.parseNotation(taskProvider)

        then:
        0 * taskProvider._

        when:
        artifact.file.get().asFile

        then:
        1 * taskProvider.get() >> task
        1 * task.getArchiveFile() >> fileProvider
        1 * fileProvider.get() >> file
        1 * file.getAsFile()
        0 * _
    }

    def "creates lazy MavenArtifact for Provider<Task> notation when task has a single output file"() {
        def task = Mock(Task)
        def taskProvider = Mock(TestTaskProvider)
        def outputs = Mock(TaskOutputsInternal)
        def fileCollection = Mock(FileCollection)

        when:
        def artifact = parser.parseNotation(taskProvider)

        then:
        0 * taskProvider._

        when:
        artifact.file.get().asFile

        then:
        1 * taskProvider.get() >> task
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> fileCollection
        1 * fileCollection.getSingleFile() >> Stub(File)
        0 * _
    }

    def "fails resolving lazy MavenArtifact for Provider<Task> notation when task has a multiple output files"() {
        def task = Mock(Task)
        def taskProvider = Mock(TestTaskProvider)
        def outputs = Mock(TaskOutputsInternal)
        def fileCollection = Mock(FileCollection)

        when:
        def artifact = parser.parseNotation(taskProvider)

        then:
        0 * taskProvider._

        when:
        1 * taskProvider.get() >> task
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> fileCollection
        1 * fileCollection.getSingleFile() >> {
            throw new RuntimeException("more than one file")
        }
        artifact.file.get().asFile

        then:
        RuntimeException e = thrown()
        e.message == "more than one file"
    }

    def "creates lazy MavenArtifact for Provider<RegularFile> notation"() {
        def provider = Mock(ProviderInternal)
        def file = Stub(File) {
            getName() >> 'picard.txt'
        }
        def regularFile = Mock(RegularFile)

        when:
        def artifact = parser.parseNotation(provider)

        then:
        0 * provider._

        when:
        1 * provider.get() >> regularFile
        1 * regularFile.getAsFile() >> file
        artifact.file.get().asFile == file

        then:
        0 * _
    }

    @Issue("https://github.com/gradle/gradle/issues/15711")
    def "creates lazy MavenArtifact for Provider<Path> notation"() {
        def provider = Mock(ProviderInternal)
        def file = Paths.get("picard.txt")
        def regularFile = Mock(RegularFile)

        when:
        def artifact = parser.parseNotation(provider)

        then:
        0 * provider._

        when:
        1 * provider.get() >> regularFile
        1 * regularFile.getAsFile() >> file.toFile()
        artifact.file.get().asFile == file.toFile()

        then:
        0 * _
    }

    interface TestTaskProvider<T extends Task> extends TaskProvider<T>, ProviderInternal<T> {}

}
