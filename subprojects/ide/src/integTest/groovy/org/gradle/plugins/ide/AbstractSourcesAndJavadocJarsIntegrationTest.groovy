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

import org.gradle.test.fixtures.ivy.IvyHttpModule
import org.gradle.test.fixtures.ivy.IvyHttpRepository
import org.gradle.test.fixtures.maven.HttpArtifact
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Ignore

abstract class AbstractSourcesAndJavadocJarsIntegrationTest extends AbstractIdeIntegrationSpec {
    @Rule HttpServer server

    def setup() {
        server.start()
        executer.requireOwnGradleUserHomeDir()
        settingsFile << "rootProject.name = 'root'"
        buildFile << baseBuildScript
    }

    private useIvyRepo(def repo) {
        buildFile << """repositories { ivy { url "$repo.uri" } }"""
    }

    private useMavenRepo(def repo) {
        buildFile << """repositories { maven { url "$repo.uri" } }"""
    }

    def "sources and javadoc jars from maven repositories are not downloaded if not required"() {
        def repo = mavenHttpRepo
        def module = repo.module("some", "module", "1.0").withSourceAndJavadoc().publish()

        when:
        useMavenRepo(repo)

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds "resolve"
    }

    def "sources and javadoc jars from maven repositories are resolved, attached and cached"() {
        def repo = mavenHttpRepo
        def module = repo.module("some", "module", "1.0")
        module.artifact(classifier: "sources")
        module.artifact(classifier: "javadoc")
        module.publish()
        module.allowAll()

        when:
        useMavenRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsSourcesAndJavadocEntry()

        when:
        server.resetExpectations()
        succeeds ideTask

        then:
        ideFileContainsSourcesAndJavadocEntry()
    }

    def "ignores missing sources and javadoc jars in maven repositories"() {
        def repo = mavenHttpRepo
        repo.module("some", "module", "1.0").publish().allowAll()

        when:
        useMavenRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    void "ignores broken source or javadoc artifacts in maven repository"() {
        def repo = mavenHttpRepo
        def module = repo.module("some", "module", "1.0")
        final sourceArtifact = module.artifact(classifier: "sources")
        final javadocArtifact = module.artifact(classifier: "javadoc")
        module.publish()

        when:
        useMavenRepo(repo)

        and:
        module.pom.expectGet()
        module.artifact.expectGet()
        sourceArtifact.expectGetBroken()
        javadocArtifact.expectGetBroken()

        expectBehaviorAfterBrokenMavenArtifact(sourceArtifact)
        expectBehaviorAfterBrokenMavenArtifact(javadocArtifact)

        then:
        succeeds ideTask
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    def "sources and javadoc jars from ivy repositories are not downloaded if not required"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        addCompleteConfigurations(module)
        module.publish()

        when:
        useIvyRepo(repo)

        and:
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds "resolve"
    }

    def "sources and javadoc jars from ivy repositories are resolved, attached and cached"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        addCompleteConfigurations(module)
        module.publish()
        module.allowAll()

        when:
        useIvyRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsSourcesAndJavadocEntry("my-sources", "my-javadoc")

        when:
        server.resetExpectations()
        succeeds ideTask

        then:
        ideFileContainsSourcesAndJavadocEntry("my-sources", "my-javadoc")
    }

    def "ignores missing sources and javadoc jars in ivy repositories"() {
        def repo = ivyHttpRepo
        final module = repo.module("some", "module", "1.0")
        addCompleteConfigurations(module)
        module.publish()

        when:
        useIvyRepo(repo)
        module.getArtifact(classifier: "my-sources").expectGetMissing()
        module.getArtifact(classifier: "my-javadoc").expectGetMissing()
        module.allowAll()

        then:
        succeeds ideTask
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    void "ignores broken source or javadoc artifacts in ivy repository"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        addCompleteConfigurations(module)
        module.publish()

        when:
        useIvyRepo(repo)

        and:
        module.ivy.expectGet()
        module.jar.expectGet()
        def sourceArtifact = module.getArtifact(classifier: "my-sources")
        def javadocArtifact = module.getArtifact(classifier: "my-javadoc")
        sourceArtifact.expectGetBroken()
        javadocArtifact.expectGetBroken()

        expectBehaviorAfterBrokenIvyArtifact(sourceArtifact)
        expectBehaviorAfterBrokenIvyArtifact(javadocArtifact)

        then:
        succeeds ideTask
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    def "sources and javadoc jars stored with maven scheme in ivy repositories are resolved and attached"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        module.configuration("default")
        module.artifact(conf: "default")
        module.undeclaredArtifact(classifier: "sources", ext: "jar")
        module.undeclaredArtifact(classifier: "javadoc", ext: "jar")
        module.publish()
        module.allowAll()

        when:
        useIvyRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsSourcesAndJavadocEntry("sources", "javadoc")
    }

    // TODO:DAZ This feature needs to be implemented and this test un-ignored
    @Ignore
    def "sources and javadoc jars from flatdir repositories are resolved and attached"() {
        file("repo/module-1.0.jar").createFile()
        file("repo/module-1.0-sources.jar").createFile()
        file("repo/module-1.0-javadoc.jar").createFile()

        when:
        buildFile << """repositories { flatDir { dir "repo" } }"""
        succeeds ideTask

        then:
        ideFileContainsSourcesAndJavadocEntry()
    }

    private static void addCompleteConfigurations(IvyHttpModule module) {
        module.configuration("default")
        module.configuration("sources")
        module.configuration("javadoc")
        module.artifact(conf: "default")
        // use uncommon sources and javadoc classifiers to prove that artifact names don't matter
        module.artifact(type: "source", classifier: "my-sources", ext: "jar", conf: "sources")
        module.artifact(type: "javadoc", classifier: "my-javadoc", ext: "jar", conf: "javadoc")
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

task resolve << {
    configurations.compile.each { println it }
}
"""
    }

    abstract String getIdeTask()

    abstract void ideFileContainsSourcesAndJavadocEntry()
    abstract void ideFileContainsSourcesAndJavadocEntry(String sourcesClassifier, String javadocClassifier)
    abstract void ideFileContainsNoSourcesAndJavadocEntry()
    abstract void expectBehaviorAfterBrokenMavenArtifact(HttpArtifact httpArtifact)
    abstract void expectBehaviorAfterBrokenIvyArtifact(HttpArtifact httpArtifact)
}