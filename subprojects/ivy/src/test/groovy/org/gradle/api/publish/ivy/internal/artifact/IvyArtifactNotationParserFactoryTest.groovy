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

import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.notations.api.NotationParser
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.HelperUtil
import spock.lang.Specification

public class IvyArtifactNotationParserFactoryTest extends Specification {
    Instantiator instantiator = new DirectInstantiator()
    def fileResolver = Mock(FileResolver)
    def taskDependency = Mock(TaskDependency)
    def publishArtifact = Stub(PublishArtifact) {
        getName() >> 'name'
        getExtension() >> 'extension'
        getType() >> 'type'
        getFile() >> new File('foo')
        getBuildDependencies() >> taskDependency
    }
    def task = Mock(Task)
    def dependencies = Collections.singleton(Mock(Task))
    NotationParser<IvyArtifact> parser = new IvyArtifactNotationParserFactory(instantiator, "1.2", fileResolver).create()

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
        ivyArtifact.name == publishArtifact.name
        ivyArtifact.extension == publishArtifact.extension
        ivyArtifact.type == publishArtifact.type
        ivyArtifact.file == publishArtifact.file

        when:
        taskDependency.getDependencies(task) >> dependencies

        then:
        ivyArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates IvyArtifact for source map notation"() {
        when:
        IvyArtifact ivyArtifact = parser.parseNotation(source: publishArtifact)

        then:
        ivyArtifact.name == publishArtifact.name
        ivyArtifact.extension == publishArtifact.extension
        ivyArtifact.type == publishArtifact.type
        ivyArtifact.file == publishArtifact.file

        when:
        taskDependency.getDependencies(task) >> dependencies

        then:
        ivyArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates and configures IvyArtifact for source map notation"() {
        when:
        IvyArtifact ivyArtifact = parser.parseNotation(source: publishArtifact, name: 'the-name', extension: "the-ext", type: "the-type")

        then:
        ivyArtifact.file == publishArtifact.file
        ivyArtifact.name == "the-name"
        ivyArtifact.extension == "the-ext"
        ivyArtifact.type == "the-type"

        when:
        taskDependency.getDependencies(task) >> dependencies

        then:
        ivyArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates IvyArtifact for ArchivePublishArtifact"() {
        when:
        def rootProject = HelperUtil.createRootProject()
        def archive = rootProject.task(type: Jar, {})
        archive.setBaseName("base-name")
        archive.setExtension('extension')

        IvyArtifact ivyArtifact = parser.parseNotation(archive)

        then:
        ivyArtifact.name == "base-name"
        ivyArtifact.extension == "extension"
        ivyArtifact.file == archive.archivePath
        ivyArtifact.buildDependencies.getDependencies(null) == [archive] as Set
    }

    def "creates IvyArtifact for file notation"() {
        given:
        File file = new File(fileName)

        when:
        IvyArtifact ivyArtifact = parser.parseNotation('some-file')

        then:
        fileResolver.resolve('some-file') >> file

        and:
        ivyArtifact.name == name
        ivyArtifact.extension == extension
        ivyArtifact.type == type
        ivyArtifact.file == file

        where:
        fileName                       | name        | extension | type
        "some-file-1.2.zip"            | "some-file" | "zip"     | "zip"
        "some-file"                    | "some-file" | null      | null
        "some-file-1.2-classifier.zip" | "some-file" | "zip"     | "zip"
    }

    def "creates IvyArtifact for file map notation"() {
        given:
        File file = new File('some-file-1.2.zip')

        when:
        def ivyArtifact = parser.parseNotation(file: 'some-file')

        then:
        fileResolver.resolve('some-file') >> file

        and:
        ivyArtifact.name == "some-file"
        ivyArtifact.extension == "zip"
        ivyArtifact.type == "zip"
        ivyArtifact.file == file
    }

    def "creates and configures IvyArtifact for file map notation"() {
        given:
        File file = new File('some-file-1.2.zip')
        def task = Mock(Task)

        when:
        IvyArtifact ivyArtifact = parser.parseNotation(file: 'some-file', name: "the-name", extension: "ext", builtBy: task)

        then:
        fileResolver.resolve('some-file') >> file

        and:
        ivyArtifact.file == file
        ivyArtifact.name == "the-name"
        ivyArtifact.extension == "ext"
        ivyArtifact.buildDependencies.getDependencies(Mock(Task)) == [task] as Set
    }

}
