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
import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

import java.awt.*

public class PublishArtifactNotationParserFactoryTest extends Specification {
    final DependencyMetaDataProvider provider = Mock()
    final Instantiator instantiator = ThreadGlobalInstantiator.getOrCreate()
    final PublishArtifactNotationParserFactory publishArtifactNotationParserFactory = new PublishArtifactNotationParserFactory(instantiator, provider)
    final NotationParser<PublishArtifact> publishArtifactNotationParser = publishArtifactNotationParserFactory.create();

    def setup() {
        Module module = Mock()
        _ * provider.module >> module
        _ * module.version >> '1.2'
    }

    def createArtifactFromPublishArtifactInstance() {
        PublishArtifact original = Mock()

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

    public void createArtifactWithNullNotationShouldThrowInvalidUserDataEx() {
        when:
        publishArtifactNotationParser.parseNotation(null)

        then:
        thrown(InvalidUserDataException)
    }

    public void createArtifactWithUnknownNotationShouldThrowInvalidUserDataEx() {
        when:
        publishArtifactNotationParser.parseNotation(new Point(1, 2))

        then:
        thrown(InvalidUserDataException)
    }
}
