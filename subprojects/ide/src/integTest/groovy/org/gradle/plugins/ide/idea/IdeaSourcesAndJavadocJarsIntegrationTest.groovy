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
package org.gradle.plugins.ide.idea

import org.gradle.plugins.ide.AbstractIdeIntegrationSpec
import org.gradle.test.fixtures.ivy.IvyHttpRepository
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Ignore

class IdeaSourcesAndJavadocJarsIntegrationTest extends AbstractIdeIntegrationSpec {
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
        runTask "idea", baseBuildScript + """repositories { maven { url "$repo.uri" } }"""

        then:
        imlFileContainsSourcesAndJavadocEntry()
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
        runTask "idea", baseBuildScript + """repositories { ivy { url "$repo.uri" } }"""

        then:
        imlFileContainsSourcesAndJavadocEntry("my-sources", "my-javadoc")

    }

    @Ignore
    def "sources and javadoc jars from flatdir repositories are resolved and attached"() {
        file("repo/module-1.0.jar").createFile()
        file("repo/module-1.0-sources.jar").createFile()
        file("repo/module-1.0-javadoc.jar").createFile()

        when:
        runTask "idea", baseBuildScript + """repositories { flatDir { dir "repo" } }"""

        then:
        imlFileContainsSourcesAndJavadocEntry()
    }

    private String getBaseBuildScript() {
"""
apply plugin: "java"
apply plugin: "idea"

dependencies {
    compile("some:module:1.0")
}

idea {
    module {
        downloadJavadoc = true
    }
}
"""
    }

    private void imlFileContainsSourcesAndJavadocEntry(sourcesClassifier = "sources", javadocClassifier = "javadoc") {
        def iml = parseImlFile("root")

        def sourcesUrl = iml.component.orderEntry.library.SOURCES.root.@url[0].text()
        assert sourcesUrl.endsWith("/module-1.0-${sourcesClassifier}.jar!/")

        def javadocUrl = iml.component.orderEntry.library.JAVADOC.root.@url[0].text()
        assert javadocUrl.endsWith("/module-1.0-${javadocClassifier}.jar!/")
    }

    private MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    private IvyHttpRepository getIvyHttpRepo() {
        return new IvyHttpRepository(server, "/repo", ivyRepo)
    }
}