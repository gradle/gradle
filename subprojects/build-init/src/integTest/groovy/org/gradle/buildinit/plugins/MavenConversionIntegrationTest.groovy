/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.PomHttpArtifact
import org.gradle.util.SetSystemProperties
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Issue

class MavenConversionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Rule
    public final SetSystemProperties systemProperties = new SetSystemProperties()

    @Rule
    public final HttpServer server = new HttpServer()

    def setup() {
        /**
         * We need to configure the local maven repository explicitly as
         * RepositorySystem.defaultUserLocalRepository is statically initialised and used when
         * creating multiple ProjectBuildingRequest.
         * */
        m2.generateUserSettingsFile(m2.mavenRepo())
        using m2
        executer.withRepositoryMirrors()
        executer.beforeExecute {
            executer.ignoreMissingSettingsFile()
        }
    }

    @ToBeFixedForInstantExecution
    def "multiModule"() {
        when:
        run 'init'

        then:
        gradleFilesGenerated()
        file("build.gradle").text.contains("options.encoding = 'UTF-8'")
        !file("webinar-war/build.gradle").text.contains("'options.encoding'")
        assertContainsPublishingConfig(file("build.gradle"), "    ")
        buildFile.text.contains(TextUtil.toPlatformLineSeparators('''
    java {
        withSourcesJar()
    }'''))
        file("webinar-impl/build.gradle").text.contains("publishing.publications.maven.artifact(testsJar)")
        file("webinar-impl/build.gradle").text.contains(TextUtil.toPlatformLineSeparators('''
java {
    withJavadocJar()
}'''))
        when:
        run 'clean', 'build'

        then: //smoke test the build artifacts
        file("webinar-api/build/libs/webinar-api-1.0-SNAPSHOT.jar").exists()
        file("webinar-impl/build/libs/webinar-impl-1.0-SNAPSHOT.jar").exists()
        file("webinar-war/build/libs/webinar-war-1.0-SNAPSHOT.war").exists()

        new DefaultTestExecutionResult(file("webinar-impl")).assertTestClassesExecuted('webinar.WebinarTest')

        when:
        run 'projects'

        then:
        output.contains """
Root project 'webinar-parent'
+--- Project ':webinar-api' - Webinar APIs
+--- Project ':webinar-impl' - Webinar implementation
\\--- Project ':webinar-war' - Webinar web application
"""
    }

    def "multiModuleWithNestedParent"() {
        when:
        run 'init'

        then:
        gradleFilesGenerated()

        when:
        run 'clean', 'build'

        then: //smoke test the build artifacts
        file("webinar-api/build/libs/webinar-api-1.0-SNAPSHOT.jar").exists()
        file("webinar-impl/build/libs/webinar-impl-1.0-SNAPSHOT.jar").exists()
        file("webinar-war/build/libs/webinar-war-1.0-SNAPSHOT.war").exists()

        new DefaultTestExecutionResult(file("webinar-impl")).assertTestClassesExecuted('webinar.WebinarTest')
    }

    @ToBeFixedForInstantExecution
    def "flatmultimodule"() {
        when:
        executer.inDirectory(file("webinar-parent"))
        run 'init'

        then:
        gradleFilesGenerated(file("webinar-parent"))

        when:
        executer.inDirectory(file("webinar-parent"))
        run 'clean', 'build'

        then: //smoke test the build artifacts
        file("webinar-api/build/libs/webinar-api-1.0-SNAPSHOT.jar").exists()
        file("webinar-impl/build/libs/webinar-impl-1.0-SNAPSHOT.jar").exists()
        file("webinar-war/build/libs/webinar-war-1.0-SNAPSHOT.war").exists()

        new DefaultTestExecutionResult(file("webinar-impl")).assertTestClassesExecuted('webinar.WebinarTest')

        when:
        executer.inDirectory(file("webinar-parent"))
        run 'projects'

        then:
        output.contains """
Root project 'webinar-parent'
+--- Project ':webinar-api' - Webinar APIs
+--- Project ':webinar-impl' - Webinar implementation
\\--- Project ':webinar-war' - Webinar web application
"""
    }

    @ToBeFixedForInstantExecution
    def "singleModule"() {
        when:
        run 'init'

        then:
        gradleFilesGenerated()
        file("settings.gradle").text.contains("rootProject.name = 'util'")
        assertContainsPublishingConfig(file("build.gradle"))

        when:
        fails 'clean', 'build'

        then:
        file("build/libs/util-2.5.jar").exists()
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("There were failing tests.")
    }

    private void assertContainsPublishingConfig(TestFile buildScript, String indent = "", List<String> additionalArchiveTasks = []) {
        def text = buildScript.text
        assert text.contains("id 'maven-publish'") || text.contains("apply plugin: 'maven-publish'")
        def configLines = ["from(components.java)"]
        configLines += additionalArchiveTasks.collect { "artifact($it)" }
        def publishingBlock = TextUtil.toPlatformLineSeparators(TextUtil.indent("""
            publishing {
                publications {
                    maven(MavenPublication) {
${TextUtil.indent(configLines.join("\n"), "                        ")}
                    }
                }
            }
            """.stripIndent().trim(), indent))
        assert text.contains(publishingBlock)
    }

    @ToBeFixedForInstantExecution
    def "singleModule with explicit project dir"() {
        setup:
        resources.maybeCopy('MavenConversionIntegrationTest/singleModule')
        def workingDir = temporaryFolder.createDir("workingDir")
        when:
        executer.inDirectory(workingDir).usingProjectDirectory(file('.'))
        run 'init'

        then:
        gradleFilesGenerated()

        when:
        fails 'clean', 'build'

        then:
        file("build/libs/util-2.5.jar").exists()
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("There were failing tests.")
    }

    @ToBeFixedForInstantExecution
    def 'sourcesJar'() {
        when: 'build is initialized'
        run 'init'

        then: 'sourcesJar task configuration is generated'
        buildFile.text.contains(TextUtil.toPlatformLineSeparators('''
            java {
                withSourcesJar()
            }
            '''.stripIndent().trim()))
        assertContainsPublishingConfig(buildFile)

        when: 'the generated task is executed'
        run 'clean', 'build', 'sourcesJar'

        then: 'the sources jar is generated'
        file('build/libs/util-2.5.jar').exists()
        file('build/libs/util-2.5-sources.jar').exists()
    }

    def 'testsJar'() {
        when: 'build is initialized'
        run 'init'

        then: 'testsJar task configuration is generated'
        buildFile.text.contains(TextUtil.toPlatformLineSeparators('''
            task testsJar(type: Jar) {
                archiveClassifier = 'tests'
                from(sourceSets.test.output)
            }
            '''.stripIndent().trim()))
        assertContainsPublishingConfig(buildFile, '', ['testsJar'])

        when: 'the generated task is executed'
        run 'clean', 'build', 'testJar'

        then: 'the tests jar is generated'
        file('build/libs/util-2.5.jar').exists()
        file('build/libs/util-2.5-tests.jar').exists()
    }

    @ToBeFixedForInstantExecution
    def 'javadocJar'() {
        when: 'build is initialized'
        run 'init'

        then: 'javadocJar task configuration is generated'
        buildFile.text.contains(TextUtil.toPlatformLineSeparators('''
            java {
                withJavadocJar()
            }
            '''.stripIndent().trim()))
        assertContainsPublishingConfig(buildFile)

        when: 'the generated task is executed'
        run 'clean', 'build', 'javadocJar'

        then: 'the javadoc jar is generated'
        file('build/libs/util-2.5.jar').exists()
        file('build/libs/util-2.5-javadoc.jar').exists()
    }

    def "enforcerplugin"() {
        when:
        run 'init'

        then:
        gradleFilesGenerated()

        and:
        buildFile.text.contains(TextUtil.toPlatformLineSeparators("""configurations.all {
    exclude(group: 'org.apache.maven')
    exclude(group: 'org.apache.maven', module: 'badArtifact')
    exclude(group: '*', module: 'badArtifact')
    exclude(group: 'broken')
}"""))
        when:
        run 'clean', 'build'

        then:
        file("build/libs/enforcerExample-1.0.jar").exists()
    }

    def "providedNotWar"() {
        when:
        run 'init'

        then:
        gradleFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build.gradle").text.contains("compileOnly 'junit:junit:4.10'")
        file("build/libs/myThing-0.0.1-SNAPSHOT.jar").exists()
    }

    def "provides decent error message when POM is invalid"() {
        setup:
        def pom = file("pom.xml")
        pom << "<project>someInvalid pom content</project>"

        when:
        fails 'init'

        then:
        failure.assertHasCause("Could not convert Maven POM $pom to a Gradle build.")
    }

    def "mavenExtensions"() {
        when:
        run 'init'
        then:
        gradleFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/testApp-1.0.jar").exists()
    }

    @Issue("GRADLE-2820")
    def "remoteparent"() {
        setup:
        withSharedResources()
        def repo = mavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())

        expectParentPomRequest(repo)

        when:
        run 'init'

        then:
        gradleFilesGenerated()

        when:
        libRequest(repo, "commons-lang", "commons-lang", "2.6")
        run 'clean', 'build'

        then:
        file("build/libs/util-2.5.jar").exists()
    }

    @Issue("GRADLE-2872")
    def "expandProperties"() {
        setup:
        withSharedResources()
        executer.withArgument("-DCOMMONS_LANG_VERSION=2.6")

        when:
        run 'init'
        then:
        gradleFilesGenerated()

        when:
        run('clean', 'build')

        then:
        file("build/libs/util-3.2.1.jar").exists()
    }

    @Issue("GRADLE-2819")
    @ToBeFixedForInstantExecution
    def "multiModuleWithRemoteParent"() {
        setup:
        withSharedResources()
        def repo = mavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())

        expectParentPomRequest(repo)

        when:
        run 'init'

        then:
        gradleFilesGenerated()

        when:
        libRequest(repo, "commons-lang", "commons-lang", 2.6)
        // Required for the 'webinar-impl' project's POM
        libRequest(repo, "junit", "junit", 4.10)
        // Required for the 'webinar-war' project's POM
        libRequest(repo, "junit", "junit", "3.8.1")
        libRequest(repo, "org.hamcrest", "hamcrest-core", 1.1)

        run 'clean', 'build'

        then: //smoke test the build artifacts
        file("webinar-api/build/libs/webinar-api-1.0-SNAPSHOT.jar").exists()
        file("webinar-impl/build/libs/webinar-impl-1.0-SNAPSHOT.jar").exists()
        file("webinar-war/build/libs/webinar-war-1.0-SNAPSHOT.war").exists()

        new DefaultTestExecutionResult(file("webinar-impl")).assertTestClassesExecuted('webinar.WebinarTest')

        when:
        run 'projects'

        then:
        output.contains """
Root project 'webinar-parent'
+--- Project ':util-parent'
+--- Project ':webinar-api' - Webinar APIs
+--- Project ':webinar-impl' - Webinar implementation
\\--- Project ':webinar-war' - Webinar web application
"""
    }

    def "kotlin dsl is not supported"() {
        given:
        resources.maybeCopy('MavenConversionIntegrationTest/singleModule')

        when:
        fails 'init', '--dsl', 'kotlin'

        then:
        failure.assertHasCause("The requested DSL 'kotlin' is not supported for 'pom' build type")
    }

    void gradleFilesGenerated(TestFile parentFolder = file(".")) {
        assert parentFolder.file("build.gradle").exists()
        assert parentFolder.file("settings.gradle").exists()
        new WrapperTestFixture(parentFolder).generated()
    }

    def libRequest(MavenHttpRepository repo, String group, String name, Object version) {
        MavenHttpModule module = repo.module(group, name, version as String)
        module.allowAll()
    }

    def expectModule(MavenHttpRepository repo, String group, String name, String version) {
        MavenHttpModule module1 = repo.module(group, name, version).publish()
        module1.allowAll()
    }

    def withSharedResources() {
        resources.maybeCopy('MavenConversionIntegrationTest/sharedResources')
    }

    PomHttpArtifact expectParentPomRequest(MavenHttpRepository repo) {
        MavenHttpModule module = repo.module('util.util.parent', 'util-parent', '3')
        module.pom.expectGet();
        module.pom.sha1.expectGet();
        module.pom.md5.expectGet();
        module.pom
    }

    MavenHttpRepository mavenHttpServer() {
        server.start()
        new MavenHttpRepository(server, '/maven', maven(file("maven_repo")));
    }
}
