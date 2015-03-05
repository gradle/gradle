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
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import spock.lang.Specification

public class MavenArtifactNotationParserFactoryTest extends Specification {
    Instantiator instantiator = DirectInstantiator.INSTANCE
    def taskDependency = Mock(TaskDependency)
    def fileNotationParser = Mock(NotationParser)
    def publishArtifact = Stub(PublishArtifact) {
        getExtension() >> 'extension'
        getClassifier() >> 'classifier'
        getFile() >> new File('foo')
        getBuildDependencies() >> taskDependency
    }
    def task = Mock(Task)
    def dependencies = Collections.singleton(Mock(Task))

    NotationParser<Object, MavenArtifact> parser

    def "setup"() {
        def fileResolver = Stub(FileResolver) {
            asNotationParser() >> fileNotationParser
        }
        parser = new MavenArtifactNotationParserFactory(instantiator, fileResolver).create()
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
        taskDependency.getDependencies(task) >> dependencies
        def mavenArtifact = parser.parseNotation(publishArtifact)

        then:
        mavenArtifact.extension == publishArtifact.extension
        mavenArtifact.classifier == publishArtifact.classifier
        mavenArtifact.file == publishArtifact.file

        and:
        mavenArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates MavenArtifact for source map notation"() {
        when:
        taskDependency.getDependencies(task) >> dependencies
        MavenArtifact mavenArtifact = parser.parseNotation(source: publishArtifact)

        then:
        mavenArtifact.extension == publishArtifact.extension
        mavenArtifact.classifier == publishArtifact.classifier
        mavenArtifact.file == publishArtifact.file

        and:
        mavenArtifact.buildDependencies.getDependencies(task) == dependencies
    }

    def "creates and configures MavenArtifact for source map notation"() {
        when:
        taskDependency.getDependencies(task) >> dependencies
        MavenArtifact mavenArtifact = parser.parseNotation(source: publishArtifact, extension: "ext", classifier: "classy")

        then:
        mavenArtifact.file == publishArtifact.file
        mavenArtifact.extension == "ext"
        mavenArtifact.classifier == "classy"

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
        mavenArtifact.extension == "zip"
        mavenArtifact.file == file
        mavenArtifact.classifier == null
    }

    def "creates MavenArtifact for ArchivePublishArtifact"() {
        when:
        def rootProject = TestUtil.createRootProject()
        def archive = rootProject.task(type: Jar, {})
        archive.setBaseName("baseName")
        archive.setExtension(archiveExtension)
        archive.setClassifier(archiveClassifier)

        MavenArtifact mavenArtifact = parser.parseNotation(archive)

        then:
        mavenArtifact.extension == artifactExtension
        mavenArtifact.classifier == artifactClassifier
        mavenArtifact.file == archive.archivePath
        mavenArtifact.buildDependencies.getDependencies(null) == [archive] as Set

        where:
        archiveClassifier | artifactClassifier | archiveExtension | artifactExtension
        "classifier"      | "classifier"       | "extension"      | "extension"
        null              | null               | null             | null
        ""                | null               | ""               | ""
    }

    def "creates MavenArtifact for file notation"() {
        when:
        File file = new File(fileName)
        fileNotationParser.parseNotation('some-file') >> file

        and:
        MavenArtifact mavenArtifact = parser.parseNotation('some-file')

        then:
        mavenArtifact.extension == extension
        mavenArtifact.file == file
        mavenArtifact.classifier == null

        where:
        fileName                       | extension
        "some-file"                    | ""
        "some-file.zip"                | "zip"
        "some-file-1.2-classifier.zip" | "zip"
    }
}
