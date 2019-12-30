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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.*
import org.junit.Rule

abstract class AbstractSourcesAndJavadocJarsIntegrationTest extends AbstractIdeIntegrationSpec {
    @Rule HttpServer server

    def setup() {
        server.start()
        executer.requireOwnGradleUserHomeDir()
        settingsFile << "rootProject.name = 'root'"
        buildFile << baseBuildScript
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

    @ToBeFixedForInstantExecution
    def "sources and javadoc jars from maven repositories are resolved, attached and cached"() {
        def repo = mavenHttpRepo
        def module = repo.module("some", "module", "1.0")
        module.artifact(classifier: "api")
        module.artifact(classifier: "sources")
        module.artifact(classifier: "javadoc")
        module.publish()
        module.allowAll()

        buildFile << """
dependencies {
    implementation 'some:module:1.0:api'
}
"""

        when:
        useMavenRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsEntry("module-1.0.jar", "module-1.0-sources.jar", "module-1.0-javadoc.jar")
        ideFileContainsEntry("module-1.0-api.jar", "module-1.0-sources.jar", "module-1.0-javadoc.jar")

        when:
        server.resetExpectations()
        succeeds ideTask

        then:
        ideFileContainsEntry("module-1.0.jar", "module-1.0-sources.jar", "module-1.0-javadoc.jar")
        ideFileContainsEntry("module-1.0-api.jar", "module-1.0-sources.jar", "module-1.0-javadoc.jar")
    }

    @ToBeFixedForInstantExecution
    def "ignores missing sources and javadoc jars in maven repositories"() {
        def repo = mavenHttpRepo
        repo.module("some", "module", "1.0").publish().allowAll()

        when:
        useMavenRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    @ToBeFixedForInstantExecution
    def "ignores broken source or javadoc artifacts in maven repository"() {
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

    @ToBeFixedForInstantExecution
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
        ideFileContainsEntry("module-1.0.jar", "module-1.0-my-sources.jar", "module-1.0-my-javadoc.jar")

        when:
        server.resetExpectations()
        succeeds ideTask

        then:
        ideFileContainsEntry("module-1.0.jar", "module-1.0-my-sources.jar", "module-1.0-my-javadoc.jar")
    }

    @ToBeFixedForInstantExecution
    def "all sources and javadoc jars resolved from ivy repo are attached to all artifacts for module"() {
        def repo = ivyHttpRepo
        def module = repo.module("some", "module", "1.0")
        addCompleteConfigurations(module)

        module.configuration("api")
        module.configuration("tests")
        module.artifact(type: "api", classifier: "api", ext: "jar", conf: "api")
        module.artifact(type: "tests", classifier: "tests", ext: "jar", conf: "tests")
        module.artifact(name: "other-source", type: "source", ext: "jar", conf: "sources")
        module.artifact(name: "other-javadoc", type: "javadoc", ext: "jar", conf: "javadoc")

        module.publish()
        module.allowAll()

        when:
        useIvyRepo(repo)
        buildFile << """
dependencies {
    implementation 'some:module:1.0:api'
    testImplementation 'some:module:1.0:tests'
}"""

        succeeds ideTask

        then:
        def sources = ["module-1.0-my-sources.jar", "other-source-1.0.jar"]
        def javadoc = ["module-1.0-my-javadoc.jar", "other-javadoc-1.0.jar"]
        ideFileContainsEntry("module-1.0.jar", sources, javadoc)
        ideFileContainsEntry("module-1.0-api.jar", sources, javadoc)
        ideFileContainsEntry("module-1.0-tests.jar", sources, javadoc)
    }

    @ToBeFixedForInstantExecution
    def "all sources jars from ivy repositories are attached when there are multiple unclassified artifacts"() {
        def repo = ivyHttpRepo

        def version = "1.0"
        def module = repo.module("some", "module", version)

        module.configuration("default")
        module.configuration("sources")
        module.configuration("javadoc")
        module.artifact(name: "foo", type: "jar", ext: "jar", conf: "default")
        module.artifact(name: "foo-sources", type: "jar", ext: "jar", conf: "sources")
        module.artifact(name: "foo-javadoc", type: "jar", ext: "jar", conf: "javadoc")
        module.artifact(name: "foo-api", type: "jar", ext: "jar", conf: "default")
        module.artifact(name: "foo-api-sources", type: "jar", ext: "jar", conf: "sources")
        module.artifact(name: "foo-api-javadoc", type: "jar", ext: "jar", conf: "javadoc")
        module.publish()
        module.allowAll()

        when:
        useIvyRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsEntry("foo-1.0.jar", ["foo-sources-1.0.jar", "foo-api-sources-1.0.jar"], ["foo-javadoc-1.0.jar", "foo-api-javadoc-1.0.jar"])
        ideFileContainsEntry("foo-api-1.0.jar", ["foo-sources-1.0.jar", "foo-api-sources-1.0.jar"], ["foo-javadoc-1.0.jar", "foo-api-javadoc-1.0.jar"])
    }

    @ToBeFixedForInstantExecution
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

    @ToBeFixedForInstantExecution
    def "ignores broken source or javadoc artifacts in ivy repository"() {
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

    @ToBeFixedForInstantExecution
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
        ideFileContainsEntry("module-1.0.jar", "module-1.0-sources.jar", "module-1.0-javadoc.jar")
    }

    @ToBeFixedForInstantExecution
    def "sources and javadoc jars from flatdir repositories are resolved and attached"() {
        file("repo/module-1.0.jar").createFile()
        file("repo/module-1.0-sources.jar").createFile()
        file("repo/module-1.0-javadoc.jar").createFile()

        when:
        buildFile << """repositories { flatDir { dir "repo" } }"""
        succeeds ideTask

        then:
        ideFileContainsEntry("module-1.0.jar", "module-1.0-sources.jar", "module-1.0-javadoc.jar")
    }

    @ToBeFixedForInstantExecution
    def "sources for gradleApi() are resolved and attached when -all distribution is used"() {
        given:
        requireGradleDistribution()
        TestFile sourcesDir = distribution.gradleHomeDir.createDir("src")
        sourcesDir.createFile("org/gradle/Test.java").writelns("package org.gradle;", "public class Test {}")

        buildScript """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation gradleApi()
            }
            """
        when:
        succeeds ideTask

        then:
        ideFileContainsGradleApiWithSources("gradle-api", sourcesDir.getPath())
    }

    @ToBeFixedForInstantExecution
    def "sources for gradleTestKit() are resolved and attached when -all distribution is used"() {
        given:
        requireGradleDistribution()
        TestFile sourcesDir = distribution.gradleHomeDir.createDir("src")
        sourcesDir.createFile("org/gradle/Test.java").writelns("package org.gradle;", "public class Test {}")

        buildScript """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation gradleTestKit()
            }
            """
        when:
        succeeds ideTask

        then:
        ideFileContainsGradleApiWithSources("gradle-test-kit", sourcesDir.getPath())
        ideFileContainsGradleApiWithSources("gradle-api", sourcesDir.getPath())
    }

    private useIvyRepo(def repo) {
        buildFile << """repositories { ivy { url "$repo.uri" } }"""
    }

    private useMavenRepo(def repo) {
        buildFile << """repositories { maven { url "$repo.uri" } }"""
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
    implementation("some:module:1.0")
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

task resolve {
    doLast {
        configurations.runtimeClasspath.each { println it }
    }
}
"""
    }

    abstract String getIdeTask()

    void ideFileContainsEntry(String jar, String sources, String javadoc) {
        ideFileContainsEntry(jar, [sources], [javadoc])
    }
    abstract void ideFileContainsEntry(String jar, List<String> sources, List<String> javadoc)
    abstract void ideFileContainsGradleApiWithSources(String apiJarPrefix, String sourcesPath)
    abstract void ideFileContainsNoSourcesAndJavadocEntry()
    abstract void expectBehaviorAfterBrokenMavenArtifact(HttpArtifact httpArtifact)
    abstract void expectBehaviorAfterBrokenIvyArtifact(HttpArtifact httpArtifact)
}
