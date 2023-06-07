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

package org.gradle.api.publish.ivy.internal.artifact

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
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

public class IvyArtifactNotationParserFactoryTest extends AbstractProjectBuilderSpec {
    Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def fileNotationParser = Mock(NotationParser)
    def task = Mock(Task)
    def taskDependency = TestFiles.taskDependencyFactory().configurableDependency(ImmutableSet.of(task))
    def publishArtifact = Stub(PublishArtifact) {
        getName() >> 'name'
        getExtension() >> 'extension'
        getType() >> 'type'
        getFile() >> new File('foo')
        getBuildDependencies() >> taskDependency
    }
    def dependencies = Collections.singleton(Mock(Task))

    NotationParser<Object, IvyArtifact> parser

    def "setup"() {
        def fileResolver = Stub(FileResolver) {
            asNotationParser() >> fileNotationParser
        }
        def identity = Stub(IvyPublicationIdentity) {
            getModule() >> 'pub-name'
        }
        parser = new IvyArtifactNotationParserFactory(instantiator, fileResolver, identity, TestFiles.taskDependencyFactory()).create()
    }

    def "directly returns IvyArtifact input"() {
        when:
        def ivyArtifact = Mock(IvyArtifact)

        then:
        parser.parseNotation(ivyArtifact) == ivyArtifact
    }

    def "creates IvyArtifact for PublishArtifact"() {
        when:
        def ivyArtifact = parser.parseNotation(publishArtifact)

        then:
        ivyArtifact.name == 'pub-name'
        ivyArtifact.extension == publishArtifact.extension
        ivyArtifact.type == publishArtifact.type
        ivyArtifact.file == publishArtifact.file
        ivyArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates IvyArtifact for source map notation"() {
        when:
        IvyArtifact ivyArtifact = parser.parseNotation(source: publishArtifact)

        then:
        ivyArtifact.name == 'pub-name'
        ivyArtifact.extension == publishArtifact.extension
        ivyArtifact.type == publishArtifact.type
        ivyArtifact.file == publishArtifact.file
        ivyArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates IvyArtifact for source map notation with file"() {
        given:
        File file = new File('some-file-1.2.zip')

        when:
        def ivyArtifact = parser.parseNotation(source: 'some-file')

        then:
        fileNotationParser.parseNotation('some-file') >> file

        and:
        ivyArtifact.name == 'pub-name'
        ivyArtifact.extension == "zip"
        ivyArtifact.type == "zip"
        ivyArtifact.file == file
    }


    def "creates and configures IvyArtifact for source map notation"() {
        when:
        IvyArtifact ivyArtifact = parser.parseNotation(source: publishArtifact, name: 'the-name', extension: "the-ext", type: "the-type")

        then:
        ivyArtifact.file == publishArtifact.file
        ivyArtifact.name == "the-name"
        ivyArtifact.extension == "the-ext"
        ivyArtifact.type == "the-type"
        ivyArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates IvyArtifact for ArchivePublishArtifact"() {
        when:
        def rootProject = TestUtil.createRootProject(temporaryFolder.testDirectory)
        def archive = rootProject.task('foo', type: Zip, {})
        archive.archiveBaseName.set("base-name")
        archive.archiveExtension.set('extension')
        archive.destinationDirectory.set(rootProject.buildDir)

        IvyArtifact ivyArtifact = parser.parseNotation(archive)

        then:
        ivyArtifact.name == 'pub-name'
        ivyArtifact.extension == "extension"
        ivyArtifact.classifier == null
        ivyArtifact.file == archive.archiveFile.get().asFile
        ivyArtifact.buildDependencies.getDependencies(null) == [archive] as Set
    }

    def "creates IvyArtifact for file notation"() {
        given:
        File file = new File(fileName)

        when:
        IvyArtifact ivyArtifact = parser.parseNotation('some-file')

        then:
        fileNotationParser.parseNotation('some-file') >> file

        and:
        ivyArtifact.name == 'pub-name'
        ivyArtifact.extension == extension
        ivyArtifact.type == type
        ivyArtifact.classifier == null
        ivyArtifact.file == file

        where:
        fileName                       | extension | type
        "some-file-1.2.zip"            | "zip"     | "zip"
        "some-file"                    | ""        | ""
        "some-file-1.2-classifier.zip" | "zip"     | "zip"
    }


    def "creates lazy IvyArtifact for Provider<AbstractArchiveTask> notation"() {
        def task = Mock(AbstractArchiveTask)
        def taskProvider = Mock(TestTaskProvider)
        def fileProvider = Mock(Provider)
        def file = Mock(RegularFile)

        when:
        def artifact = parser.parseNotation(taskProvider)

        then:
        0 * taskProvider._

        when:
        artifact.file

        then:
        1 * taskProvider.get() >> task
        1 * task.getArchiveFile() >> fileProvider
        1 * fileProvider.get() >> file
        1 * file.getAsFile()
        0 * _
    }

    def "creates lazy IvyArtifact for Provider<Task> notation when task has a single output file"() {
        def task = Mock(Task)
        def taskProvider = Mock(TestTaskProvider)
        def outputs = Mock(TaskOutputsInternal)
        def fileCollection = Mock(FileCollection)

        when:
        def artifact = parser.parseNotation(taskProvider)

        then:
        0 * taskProvider._

        when:
        artifact.file

        then:
        1 * taskProvider.get() >> task
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> fileCollection
        1 * fileCollection.getSingleFile() >> Stub(File)
        0 * _
    }

    def "fails resolving lazy IvyArtifact for Provider<Task> notation when task has a multiple output files"() {
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
        artifact.file

        then:
        RuntimeException e = thrown()
        e.message == "more than one file"
    }

    def "creates lazy IvyArtifact for Provider<RegularFile> notation"() {
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
        artifact.file

        then:
        0 * _
    }

    interface TestTaskProvider<T extends Task> extends TaskProvider<T>, ProviderInternal {}

}
