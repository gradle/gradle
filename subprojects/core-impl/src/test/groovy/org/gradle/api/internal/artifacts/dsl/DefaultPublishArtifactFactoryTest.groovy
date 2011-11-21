/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl;


import java.awt.Point
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.DirectInstantiator
import org.gradle.api.internal.Instantiator
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import spock.lang.Specification
import org.gradle.api.Task

/**
 * @author Hans Dockter
 */
public class DefaultPublishArtifactFactoryTest extends Specification {
    final DependencyMetaDataProvider provider = Mock()
    final Instantiator instantiator = new DirectInstantiator()
    final DefaultPublishArtifactFactory publishArtifactFactory = new DefaultPublishArtifactFactory(instantiator, provider)

    def setup() {
        Module module = Mock()
        _ * provider.module >> module
        _ * module.version >> '1.2'
    }

    def createArtifactFromPublishArtifactInstance() {
        PublishArtifact original = Mock()

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(original)

        then:
        publishArtifact == original
    }

    def createArtifactFromArchiveTask() {
        AbstractArchiveTask archiveTask = Mock()
        archiveTask.getArchivePath() >> new File("")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(archiveTask)

        then:
        publishArtifact instanceof ArchivePublishArtifact
        publishArtifact.archiveTask == archiveTask
    }

    def createArtifactFromFile() {
        def file = new File("some.zip")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(file)

        then:
        publishArtifact instanceof DefaultPublishArtifact
        publishArtifact.file == file
    }

    def createArtifactFromFileInMap() {
        Task task = Mock()
        def file = new File("some.zip")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(file: file, type: 'someType', builtBy: task)

        then:
        publishArtifact instanceof DefaultPublishArtifact
        publishArtifact.file == file
        publishArtifact.type == 'someType'
        publishArtifact.buildDependencies.getDependencies(null) == [task] as Set
    }

    def determinesArtifactPropertiesFromFileName() {
        def file1 = new File("some.zip")
        def file2 = new File("some.zip.zip")
        def file3 = new File(".zip")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(file1)

        then:
        publishArtifact.name == 'some'
        publishArtifact.type == 'zip'
        publishArtifact.extension == 'zip'
        publishArtifact.classifier == null

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file2)

        then:
        publishArtifact.name == 'some.zip'
        publishArtifact.type == 'zip'
        publishArtifact.extension == 'zip'
        publishArtifact.classifier == null

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file3)

        then:
        publishArtifact.name == ''
        publishArtifact.type == 'zip'
        publishArtifact.extension == 'zip'
        publishArtifact.classifier == null
    }

    def handlesFileWithNoExtension() {
        def file = new File("some-file")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(file)

        then:
        publishArtifact.file == file
        publishArtifact.name == 'some-file'
        publishArtifact.type == null
        publishArtifact.extension == null
        publishArtifact.classifier == null
    }

    def removesProjectVersionFromFileName() {
        def file1 = new File("some-file-1.2.jar")
        def file2 = new File("some-file-1.2-1.2.jar")
        def file3 = new File("some-file-1.22.jar")
        def file4 = new File("some-file-1.2.jar.jar")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(file1)

        then:
        publishArtifact.name == 'some-file'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == null

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file2)

        then:
        publishArtifact.name == 'some-file-1.2'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == null

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file3)

        then:
        publishArtifact.name == 'some-file-1.22'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == null

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file4)

        then:
        publishArtifact.name == 'some-file-1.2.jar'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == null
    }

    def determinesClassifierFromFileName() {
        def file1 = new File("some-file-1.2-classifier.jar")
        def file2 = new File("some-file-1.2-classifier-1.2.jar")
        def file3 = new File("-1.2-classifier.jar")
        def file4 = new File("some-file-1.2-classifier")
        def file5 = new File("some-file-1.2-.jar")

        when:
        def publishArtifact = publishArtifactFactory.parseNotation(file1)

        then:
        publishArtifact.name == 'some-file'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == 'classifier'

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file2)

        then:
        publishArtifact.name == 'some-file-1.2-classifier'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == null

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file3)

        then:
        publishArtifact.name == ''
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == 'classifier'

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file4)

        then:
        publishArtifact.name == 'some-file'
        publishArtifact.type == null
        publishArtifact.extension == null
        publishArtifact.classifier == 'classifier'

        when:
        publishArtifact = publishArtifactFactory.parseNotation(file5)

        then:
        publishArtifact.name == 'some-file'
        publishArtifact.type == 'jar'
        publishArtifact.extension == 'jar'
        publishArtifact.classifier == null
    }

    public void createArtifactWithNullNotationShouldThrowInvalidUserDataEx() {
        when:
        publishArtifactFactory.parseNotation(null)

        then:
        thrown(InvalidUserDataException)
    }

    public void createArtifactWithUnknownNotationShouldThrowInvalidUserDataEx() {
        when:
        publishArtifactFactory.parseNotation(new Point(1, 2))

        then:
        thrown(InvalidUserDataException)
    }
}
