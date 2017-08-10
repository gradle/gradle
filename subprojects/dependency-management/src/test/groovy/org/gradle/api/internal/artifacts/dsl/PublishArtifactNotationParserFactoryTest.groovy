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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.artifacts.publish.DecoratingPublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.util.TextUtil
import spock.lang.Specification

import java.awt.*

class PublishArtifactNotationParserFactoryTest extends Specification {
    final DependencyMetaDataProvider provider = Mock()
    final TaskResolver taskResolver = Mock()
    final Instantiator instantiator = ThreadGlobalInstantiator.getOrCreate()
    final PublishArtifactNotationParserFactory publishArtifactNotationParserFactory = new PublishArtifactNotationParserFactory(instantiator, provider, taskResolver)
    final NotationParser<Object, PublishArtifact> publishArtifactNotationParser = publishArtifactNotationParserFactory.create();

    def setup() {
        Module module = Mock()
        _ * provider.module >> module
        _ * module.version >> '1.2'
    }

    def createArtifactFromPublishArtifactInstance() {
        def original = Stub(PublishArtifact)

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(original)

        then:
        publishArtifact instanceof DecoratingPublishArtifact
    }

    def createArtifactFromConfigurablePublishArtifactInstance() {
        ConfigurablePublishArtifact original = Mock()

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(original)

        then:
        publishArtifact == original
    }

    def createArtifactFromArchiveTask() {
        AbstractArchiveTask archiveTask = Mock()
        archiveTask.getArchivePath() >> new File("")

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(archiveTask)

        then:
        publishArtifact instanceof ArchivePublishArtifact
        publishArtifact.archiveTask == archiveTask
    }

    def createArtifactFromFile() {
        def file = new File("some.zip")

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(file)

        then:
        publishArtifact instanceof DefaultPublishArtifact
        publishArtifact.file == file
        publishArtifact.name == 'some'
        publishArtifact.type == 'zip'
        publishArtifact.extension == 'zip'
        publishArtifact.classifier == null
    }

    def "creates artifact from extension-less file"() {
        def file = new File("someFile")

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(file)

        then:
        publishArtifact instanceof DefaultPublishArtifact
        publishArtifact.file == file
        publishArtifact.name == 'someFile'
        publishArtifact.type == ''
        publishArtifact.extension == ''
        publishArtifact.classifier == null
    }

    def "create artifact from buildable RegularFile provider"() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def provider = Mock(BuildableProvider)
        def value = Mock(RegularFile)
        def file1 = new File("classes-1.zip")

        _ * provider.get() >> value
        _ * value.get() >> file1
        _ * provider.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(task1); context.add(task2) }

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)

        then:
        publishArtifact instanceof DecoratingPublishArtifact
        publishArtifact.file == file1
        publishArtifact.name == "classes-1"
        publishArtifact.extension == "zip"
        publishArtifact.classifier == null

        when:
        def deps = publishArtifact.buildDependencies

        then:
        deps.getDependencies(null) == [task1, task2] as Set
    }

    def "create artifact from buildable Directory provider"() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def provider = Mock(BuildableProvider)
        def value = Mock(Directory)
        def file1 = new File("classes-1.dir")

        _ * provider.get() >> value
        _ * value.get() >> file1
        _ * provider.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(task1); context.add(task2) }

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)

        then:
        publishArtifact instanceof DecoratingPublishArtifact
        publishArtifact.file == file1
        publishArtifact.name == "classes-1"
        publishArtifact.extension == "dir"
        publishArtifact.classifier == null

        when:
        def deps = publishArtifact.buildDependencies

        then:
        deps.getDependencies(null) == [task1, task2] as Set
    }

    def "fails when provider returns an unsupported type"() {
        def provider = Mock(BuildableProvider)

        given:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)
        provider.get() >> "broken"

        when:
        publishArtifact.file

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Cannot convert provided value (broken) to a file."
    }

    def createArtifactFromFileInMap() {
        Task task = Mock()
        def file = new File("some-file-1.2-classifier.zip")

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(file: file, type: 'someType', builtBy: task)

        then:
        publishArtifact instanceof DefaultPublishArtifact
        publishArtifact.file == file
        publishArtifact.type == 'someType'
        publishArtifact.name == 'some-file'
        publishArtifact.extension == 'zip'
        publishArtifact.classifier == 'classifier'
        publishArtifact.buildDependencies.getDependencies(null) == [task] as Set
    }

    def createArtifactWithNullNotationShouldThrowInvalidUserDataEx() {
        when:
        publishArtifactNotationParser.parseNotation(null)

        then:
        thrown(UnsupportedNotationException)
    }

    def createArtifactWithUnknownNotationShouldThrowInvalidUserDataEx() {
        when:
        publishArtifactNotationParser.parseNotation(new Point(1, 2))

        then:
        def e = thrown(UnsupportedNotationException)
        e.message.contains(TextUtil.toPlatformLineSeparators("""
The following types/formats are supported:
  - Instances of ConfigurablePublishArtifact.
  - Instances of PublishArtifact.
  - Instances of AbstractArchiveTask, for example jar.
  - Instances of Provider<RegularFile>
  - Instances of Provider<Directory>
  - Instances of File.
  - Maps with 'file' key"""))
    }

    interface BuildableProvider extends Provider, TaskDependencyContainer {
    }
}
