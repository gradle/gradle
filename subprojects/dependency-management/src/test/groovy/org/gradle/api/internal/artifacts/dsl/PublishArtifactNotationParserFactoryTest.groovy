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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.publish.DefaultConfigurablePublishArtifact
import org.gradle.api.internal.model.InstantiatorBackedObjectFactory
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
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
    final ObjectFactory objectFactory = new InstantiatorBackedObjectFactory(instantiator)
    final ProviderFactory providerFactory = new DefaultProviderFactory()

    final PublishArtifactNotationParserFactory publishArtifactNotationParserFactory = new PublishArtifactNotationParserFactory(instantiator, provider, taskResolver, objectFactory, providerFactory)
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
        publishArtifact instanceof ConfigurablePublishArtifact
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
        archiveTask.getArchiveBaseName() >> objectFactory.property(String).value("some")
        archiveTask.getArchiveExtension() >> objectFactory.property(String).value("zip")
        archiveTask.getArchiveClassifier() >> objectFactory.property(String).value("")
        archiveTask.getArchiveAppendix() >> objectFactory.property(String).value("")
        archiveTask.getArchiveFile() >> Providers.of(Mock(RegularFile))

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(archiveTask)

        then:
        publishArtifact instanceof ConfigurablePublishArtifact
        publishArtifact.name == "some"
        publishArtifact.extension == "zip"
        publishArtifact.type == "zip"
        publishArtifact.classifier == ""
    }

    def createArtifactFromFile() {
        def file = new File("some.zip")

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(file)

        then:
        publishArtifact instanceof DefaultConfigurablePublishArtifact
        publishArtifact.file == file
        publishArtifact.name == 'some'
        publishArtifact.type == 'zip'
        publishArtifact.extension == 'zip'
        publishArtifact.classifier == null

        when:
        def deps = publishArtifact.buildDependencies

        then:
        deps.getDependencies(null).empty
    }

    def "creates artifact from extension-less file"() {
        def file = new File("someFile")

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(file)

        then:
        publishArtifact instanceof DefaultConfigurablePublishArtifact
        publishArtifact.file == file
        publishArtifact.name == 'someFile'
        publishArtifact.type == ''
        publishArtifact.extension == ''
        publishArtifact.classifier == null
    }

    def "create artifact from RegularFile"() {
        def value = Mock(RegularFile)
        def file = new File("classes-1.zip")

        _ * value.getAsFile() >> file

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(value)

        then:
        publishArtifact instanceof ConfigurablePublishArtifact
        publishArtifact.file == file
        publishArtifact.name == "classes-1"
        publishArtifact.extension == "zip"
        publishArtifact.classifier == null
        publishArtifact.buildDependencies.getDependencies(null).isEmpty()
    }

    def "create artifact from Directory"() {
        def value = Mock(Directory)
        def file1 = new File("classes-1.dir")

        _ * value.getAsFile() >> file1

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(value)

        then:
        publishArtifact instanceof ConfigurablePublishArtifact
        publishArtifact.file == file1
        publishArtifact.name == "classes-1"
        publishArtifact.extension == 'dir'
        publishArtifact.classifier == null
        publishArtifact.buildDependencies.getDependencies(null).isEmpty()
    }

    def "create artifact from File provider"() {
        def file1 = new File("classes-1.zip")
        def provider = Providers.of(file1)

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)

        then:
        publishArtifact instanceof ConfigurablePublishArtifact
        publishArtifact.file == file1
        publishArtifact.name == "classes-1"
        publishArtifact.extension == "zip"
        publishArtifact.classifier == ''

        when:
        def deps = publishArtifact.buildDependencies

        then:
        deps.getDependencies(null).empty
    }

    def "create artifact from buildable RegularFile provider"() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def value = Mock(RegularFile)
        def provider = Providers.of(value)
        def file1 = new File("classes-1.zip")

        _ * value.getAsFile() >> file1

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)

        then:
        publishArtifact instanceof ConfigurablePublishArtifact
        publishArtifact.file == file1
        publishArtifact.name == "classes-1"
        publishArtifact.extension == "zip"
        publishArtifact.classifier == ''

        when:
        def deps = publishArtifact.buildDependencies

        then:
        deps.getDependencies(null) == [task1, task2] as Set
    }

    def "create artifact from buildable Directory provider"() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def value = Mock(Directory)
        def provider = Providers.of(value)
        def file1 = new File("classes-1.dir")

        _ * value.getAsFile() >> file1

        when:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)

        then:
        publishArtifact instanceof ConfigurablePublishArtifact
        publishArtifact.file == file1
        publishArtifact.name == "classes-1"
        publishArtifact.extension == "dir"
        publishArtifact.classifier == ''

        when:
        def deps = publishArtifact.buildDependencies

        then:
        deps.getDependencies(null) == [task1, task2] as Set
    }

    def "fails when provider returns an unsupported type"() {
        def provider = Providers.of("broken")

        given:
        def publishArtifact = publishArtifactNotationParser.parseNotation(provider)

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
        publishArtifact instanceof DefaultConfigurablePublishArtifact
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
        e.message.contains(TextUtil.toPlatformLineSeparators('''
The following types/formats are supported:
  - Instances of ConfigurablePublishArtifact.
  - Instances of PublishArtifact.
  - Instances of AbstractArchiveTask, for example jar.
  - Instances of Provider<RegularFile>.
  - Instances of Provider<Directory>.
  - Instances of Provider<File>.
  - Instances of RegularFile.
  - Instances of Directory.
  - Instances of File.
  - Maps with 'file' key'''))
    }
}
