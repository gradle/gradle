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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.GroovyCoverage
import org.junit.Rule

import static org.gradle.util.internal.GroovyDependencyUtil.groovyGroupName

abstract class AbstractSourcesAndJavadocJarsIntegrationTest extends AbstractIdeIntegrationSpec {
    @Rule
    HttpServer server

    String groovyVersion = GroovyCoverage.CURRENT_STABLE

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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
    def "ignores missing sources and javadoc jars in maven repositories"() {
        def repo = mavenHttpRepo
        repo.module("some", "module", "1.0").publish().allowAll()

        when:
        useMavenRepo(repo)
        succeeds ideTask

        then:
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    @ToBeFixedForConfigurationCache
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

        sourceArtifact.expectHead()
        sourceArtifact.expectGetBroken()

        javadocArtifact.expectHead()
        javadocArtifact.expectGetBroken()


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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

        then:
        succeeds ideTask
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
    @Requires(IntegTestPreconditions.IsDaemonExecutor)
    def "does not download gradleApi() sources when sources download is disabled"() {
        given:
        executer.withEnvironmentVars('GRADLE_REPO_OVERRIDE': "$server.uri/")

        buildFile """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation gradleApi()
            }

            idea.module.downloadSources = false
            eclipse.classpath.downloadSources = false
            """
        when:
        succeeds ideTask

        then:
        assertSourcesDirectoryDoesNotExistInDistribution()
        ideFileContainsGradleApi("gradle-api")
    }

    @ToBeFixedForConfigurationCache
    @Requires(IntegTestPreconditions.IsDaemonExecutor)
    def "does not download gradleApi() sources when offline"() {
        given:
        executer.withEnvironmentVars('GRADLE_REPO_OVERRIDE': "$server.uri/")

        buildFile """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation gradleApi()
            }
            """
        when:
        args("--offline")
        succeeds ideTask

        then:
        assertSourcesDirectoryDoesNotExistInDistribution()
        ideFileContainsGradleApi("gradle-api")
    }

    @ToBeFixedForConfigurationCache
    @Requires(UnitTestPreconditions.StableGroovy) // localGroovy() version cannot be swapped-out when a snapshot Groovy build is used
    def "sources for localGroovy() are downloaded and attached"() {
        given:
        def repo = givenGroovyExistsInGradleRepo()
        executer.withEnvironmentVars('GRADLE_LIBS_REPO_OVERRIDE': "$repo.uri/")

        buildFile """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation localGroovy()
            }
            """

        when:
        succeeds ideTask

        then:
        ideFileContainsEntry("groovy-${groovyVersion}.jar", ["groovy-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-ant-${groovyVersion}.jar", ["groovy-ant-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-astbuilder-${groovyVersion}.jar", ["groovy-astbuilder-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-console-${groovyVersion}.jar", ["groovy-console-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-datetime-${groovyVersion}.jar", ["groovy-datetime-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-dateutil-${groovyVersion}.jar", ["groovy-dateutil-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-groovydoc-${groovyVersion}.jar", ["groovy-groovydoc-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-json-${groovyVersion}.jar", ["groovy-json-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-nio-${groovyVersion}.jar", ["groovy-nio-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-sql-${groovyVersion}.jar", ["groovy-sql-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-templates-${groovyVersion}.jar", ["groovy-templates-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-test-${groovyVersion}.jar", ["groovy-test-${groovyVersion}-sources.jar"], [])
        ideFileContainsEntry("groovy-xml-${groovyVersion}.jar", ["groovy-xml-${groovyVersion}-sources.jar"], [])
    }

    @ToBeFixedForConfigurationCache
    @Requires(UnitTestPreconditions.StableGroovy) // localGroovy() version cannot be swapped-out when a snapshot Groovy build is used
    def "sources for localGroovy() are downloaded and attached when using gradleApi()"() {
        given:
        def repo = givenGroovyExistsInGradleRepo()
        executer.withEnvironmentVars('GRADLE_LIBS_REPO_OVERRIDE': "$repo.uri/")

        buildFile """
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
        ideFileContainsEntry("groovy-${groovyVersion}.jar", ["groovy-${groovyVersion}-sources.jar"], [])
    }

    @ToBeFixedForConfigurationCache
    @Requires(
        value = [UnitTestPreconditions.StableGroovy, IntegTestPreconditions.NotEmbeddedExecutor],
        reason = "localGroovy() version cannot be swapped-out when a snapshot Groovy build is used"
    )
    def "sources for localGroovy() are downloaded and attached when using gradleTestKit()"() {
        given:
        def repo = givenGroovyExistsInGradleRepo()
        executer.withEnvironmentVars('GRADLE_LIBS_REPO_OVERRIDE': "$repo.uri/")

        buildFile """
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
        ideFileContainsEntry("groovy-${groovyVersion}.jar", ["groovy-${groovyVersion}-sources.jar"], [])
    }

    @ToBeFixedForConfigurationCache
    def "does not download localGroovy() sources when sources download is disabled"() {
        given:
        executer.withEnvironmentVars('GRADLE_LIBS_REPO_OVERRIDE': "$server.uri/")

        buildFile """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation localGroovy()
            }

            idea.module.downloadSources = false
            eclipse.classpath.downloadSources = false
            """

        when:
        succeeds ideTask

        then:
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    @ToBeFixedForConfigurationCache
    def "does not download localGroovy() sources when offline"() {
        given:
        executer.withEnvironmentVars('GRADLE_LIBS_REPO_OVERRIDE': "$server.uri/")

        buildFile """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation localGroovy()
            }
            """

        when:
        args("--offline")
        succeeds ideTask

        then:
        ideFileContainsNoSourcesAndJavadocEntry()
    }

    @ToBeFixedForConfigurationCache
    @Requires(UnitTestPreconditions.StableGroovy) // localGroovy() version cannot be swapped-out when a snapshot Groovy build is used
    def "does not add project repository to download localGroovy() sources"() {
        given:
        def repo = givenGroovyExistsInGradleRepo()
        executer.withEnvironmentVars('GRADLE_LIBS_REPO_OVERRIDE': "$repo.uri/")
        settingsFile << """
            dependencyResolutionManagement { repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS }
        """

        buildFile """
            apply plugin: "java"
            apply plugin: "idea"
            apply plugin: "eclipse"

            dependencies {
                implementation localGroovy()
            }
        """

        when:
        succeeds ideTask

        then:
        ideFileContainsEntry("groovy-${groovyVersion}.jar", ["groovy-${groovyVersion}-sources.jar"], [])
    }

    void assertSourcesDirectoryDoesNotExistInDistribution() {
        gradleDistributionSrcDir().assertDoesNotExist()
    }

    private TestFile gradleDistributionSrcDir() {
        return new TestFile(distribution.gradleHomeDir, "src")
    }

    def givenGroovyExistsInGradleRepo() {
        def repo = mavenHttpRepo
        publishGroovyModuleWithSources(repo, "groovy")
        publishGroovyModuleWithSources(repo, "groovy-ant")
        publishGroovyModuleWithSources(repo, "groovy-astbuilder")
        publishGroovyModuleWithSources(repo, "groovy-console")
        publishGroovyModuleWithSources(repo, "groovy-docgenerator")
        publishGroovyModuleWithSources(repo, "groovy-datetime")
        publishGroovyModuleWithSources(repo, "groovy-dateutil")
        publishGroovyModuleWithSources(repo, "groovy-groovydoc")
        publishGroovyModuleWithSources(repo, "groovy-json")
        publishGroovyModuleWithSources(repo, "groovy-nio")
        publishGroovyModuleWithSources(repo, "groovy-sql")
        publishGroovyModuleWithSources(repo, "groovy-swing")
        publishGroovyModuleWithSources(repo, "groovy-templates")
        publishGroovyModuleWithSources(repo, "groovy-test")
        publishGroovyModuleWithSources(repo, "groovy-xml")
        return repo
    }

    def publishGroovyModuleWithSources(MavenHttpRepository repo, String artifactId) {
        def module = repo.module(groovyGroupName(groovyVersion), artifactId, groovyVersion)
        module.artifact(classifier: "sources")
        module.publish()
        module.allowAll()
    }

    private useIvyRepo(def repo) {
        buildFile << """repositories { ivy { url = "$repo.uri" } }"""
    }

    private useMavenRepo(def repo) {
        buildFile << """repositories { maven { url = "$repo.uri" } }"""
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
    def runtimeClasspath = configurations.runtimeClasspath
    doLast {
        runtimeClasspath.each { println it }
    }
}
"""
    }

    abstract String getIdeTask()

    void ideFileContainsEntry(String jar, String sources, String javadoc) {
        ideFileContainsEntry(jar, [sources], [javadoc])
    }

    abstract void ideFileContainsEntry(String jar, List<String> sources, List<String> javadoc)

    abstract void ideFileContainsGradleApi(String apiJarPrefix)

    abstract void ideFileContainsNoSourcesAndJavadocEntry()


}
