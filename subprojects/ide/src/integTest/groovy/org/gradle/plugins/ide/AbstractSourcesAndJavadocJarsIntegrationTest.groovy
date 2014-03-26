/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide
import org.gradle.test.fixtures.ivy.IvyHttpRepository
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

abstract class AbstractSourcesAndJavadocJarsIntegrationTest extends AbstractIdeIntegrationSpec {
    @Rule HttpServer server

    def "sources and javadoc jars from maven repositories are resolved and attached"() {
        def repo = mavenHttpRepo
        def module = repo.module("some", "module", "1.0")
        module.artifact(classifier: "sources")
        module.artifact(classifier: "javadoc")
        module.publish()
        module.allowAll()
        server.start()

        when:
        executeIdeTask(baseBuildScript + """repositories { maven { url "$repo.uri" } }""")

        then:
        ideFileContainsSourcesAndJavadocEntry()
    }

    def "sources and javadoc jars from ivy repositories are resolved and attached"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        module.configuration("default")
        module.configuration("sources")
        module.configuration("javadoc")
        module.artifact(conf: "default")
        // use uncommon sources and javadoc classifiers to prove that artifact names don't matter
        module.artifact(type: "source", classifier: "my-sources", ext: "jar", conf: "sources")
        module.artifact(type: "javadoc", classifier: "my-javadoc", ext: "jar", conf: "javadoc")
        module.publish()
        module.allowAll()
        server.start()

        when:
        executeIdeTask(baseBuildScript + """repositories { ivy { url "$repo.uri" } }""")

        then:
        ideFileContainsSourcesAndJavadocEntry("my-sources", "my-javadoc")

    }

    def "sources and javadoc jars stored with maven scheme in ivy repositories are resolved and attached"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        module.configuration("default")
        module.artifact(conf: "default")
        module.getArtifact(classifier: "sources", ext: "jar").file << "content"
        module.getArtifact(classifier: "javadoc", ext: "jar").file << "content"
        module.publish()
        module.allowAll()
        server.start()

        when:
        executeIdeTask(baseBuildScript + """repositories { ivy { url "$repo.uri" } }""")

        then:
        ideFileContainsSourcesAndJavadocEntry("sources", "javadoc")
    }

    def "sources and javadoc jars from flatdir repositories are resolved and attached"() {
        file("repo/module-1.0.jar").createFile()
        file("repo/module-1.0-sources.jar").createFile()
        file("repo/module-1.0-javadoc.jar").createFile()

        when:
        executeIdeTask(baseBuildScript + """repositories { flatDir { dir "repo" } }""")

        then:
        ideFileContainsSourcesAndJavadocEntry()
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    IvyHttpRepository getIvyHttpRepo() {
        return new IvyHttpRepository(server, "/repo", ivyRepo)
    }

    String getBaseBuildScript() {
        """
apply plugin: "java"
apply plugin: "idea"
apply plugin: "eclipse"

dependencies {
    compile("some:module:1.0")
}

idea {
    module {
        downloadJavadoc = true
    }
}

eclipse {
    classpath {
        downloadJavadoc = true
    }
}
"""
    }

    abstract void executeIdeTask(String buildScript)

    abstract void ideFileContainsSourcesAndJavadocEntry()
    abstract void ideFileContainsSourcesAndJavadocEntry(String sourcesClassifier, String javadocClassifier)
}