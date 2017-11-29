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

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.PomHttpArtifact
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class MavenConversionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Rule
    public final SetSystemProperties systemProperties = new SetSystemProperties()

    @Rule
    public final HttpServer server = new HttpServer()

    def setup() {
        requireGradleDistribution()
        /**
         * We need to configure the local maven repository explicitly as
         * RepositorySystem.defaultUserLocalRepository is statically initialised and used when
         * creating multiple ProjectBuildingRequest.
         * */
        m2.generateUserSettingsFile(m2.mavenRepo())
        using m2
    }

    @Unroll
    def "multiModule with #scriptDsl build scripts"() {
        given:
        withMavenBuild("multiModule")

        and:
        def dslFixture = ScriptDslFixture.of(scriptDsl, testDirectory)

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        dslFixture.assertGradleFilesGenerated()

        and:
        dslFixture.buildFile.assertContents(dslFixture.containsStringAssignment('options.encoding', 'UTF-8'))
        dslFixture.scriptFile("webinar-war/build").assertContents(not(containsString("options.encoding")))

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

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "multiModuleWithNestedParent with #scriptDsl build scripts"() {
        given:
        withMavenBuild('multiModuleWithNestedParent')

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        run 'clean', 'build'

        then: //smoke test the build artifacts
        file("webinar-api/build/libs/webinar-api-1.0-SNAPSHOT.jar").exists()
        file("webinar-impl/build/libs/webinar-impl-1.0-SNAPSHOT.jar").exists()
        file("webinar-war/build/libs/webinar-war-1.0-SNAPSHOT.war").exists()

        new DefaultTestExecutionResult(file("webinar-impl")).assertTestClassesExecuted('webinar.WebinarTest')

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "flatmultimodule with #scriptDsl build scripts"() {
        given:
        withMavenBuild('flatmultimodule')

        when:
        executer.inDirectory(file("webinar-parent"))
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, file("webinar-parent")).assertGradleFilesGenerated()

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

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "singleModule with #scriptDsl build scripts"() {
        given:
        withMavenBuild('singleModule')

        when:
        executer.withArgument("-d")
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        //TODO this build should fail because the TestNG test is failing
        //however the plugin does not generate testNG for single module project atm (bug)
        //def failure = runAndFail('clean', 'build')  //assert if fails for the right reason
        run 'clean', 'build'
        then:
        file("build/libs/util-2.5.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "singleModule with #scriptDsl build scripts and explicit project dir"() {
        setup:
        withMavenBuild('singleModule')
        def workingDir = temporaryFolder.createDir("workingDir")
        when:
        executer.inDirectory(workingDir).usingProjectDirectory(file('.'))
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        //TODO this build should fail because the TestNG test is failing
        //however the plugin does not generate testNG for single module project atm (bug)
        //def failure = runAndFail('clean', 'build')  //assert if fails for the right reason
        run 'clean', 'build'
        then:
        file("build/libs/util-2.5.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "testjar with #scriptDsl build scripts"() {
        given:
        withMavenBuild('testjar')

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/testjar-2.5.jar").exists()
        file("build/libs/testjar-2.5-tests.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "enforcerplugin with #scriptDsl build scripts"() {
        given:
        withMavenBuild('enforcerplugin')

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        def dslFixture = ScriptDslFixture.of(scriptDsl, testDirectory)
        dslFixture.assertGradleFilesGenerated()

        and:
        switch (scriptDsl) {
            case KOTLIN:
                dslFixture.buildFile.text.contains("""configurations.all {
exclude(group = "org.apache.maven")
exclude(group = "org.apache.maven", module = "badArtifact")
exclude(group = "*", module = "badArtifact")
}""")
                break
            case GROOVY:
            default:
                dslFixture.buildFile.text.contains("""configurations.all {
it.exclude group: 'org.apache.maven'
it.exclude group: 'org.apache.maven', module: 'badArtifact'
it.exclude group: '*', module: 'badArtifact'
}""")
        }

        when:
        run 'clean', 'build'

        then:
        file("build/libs/enforcerExample-1.0.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "providedNotWar with #scriptDsl build scripts"() {
        given:
        withMavenBuild('providedNotWar')

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/myThing-0.0.1-SNAPSHOT.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
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

    @Unroll
    def "mavenExtensions with #scriptDsl build scripts"() {
        given:
        withMavenBuild('mavenExtensions')

        when:
        run 'init', '--dsl', scriptDsl.id
        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/testApp-1.0.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Issue("GRADLE-2820")
    @Unroll
    def "remoteparent with #scriptDsl build scripts"() {
        setup:
        withMavenBuild('remoteparent')
        withSharedResources()
        def repo = mavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())

        expectParentPomRequest(repo)

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        libRequest(repo, "commons-lang", "commons-lang", "2.6")
        run 'clean', 'build'

        then:
        file("build/libs/util-2.5.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Issue("GRADLE-2872")
    @Unroll
    def "expandProperties with #scriptDsl build scripts"() {
        setup:
        withMavenBuild('expandProperties')
        withSharedResources()
        executer.withArgument("-DCOMMONS_LANG_VERSION=2.6")

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

        when:
        run('clean', 'build')

        then:
        file("build/libs/util-3.2.1.jar").exists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Issue("GRADLE-2819")
    @Unroll
    def "multiModuleWithRemoteParent with #scriptDsl build scripts"() {
        setup:
        withMavenBuild('multiModuleWithRemoteParent')
        withSharedResources()
        def repo = mavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())

        expectParentPomRequest(repo)

        when:
        run 'init', '--dsl', scriptDsl.id

        then:
        ScriptDslFixture.of(scriptDsl, testDirectory).assertGradleFilesGenerated()

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

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def libRequest(MavenHttpRepository repo, String group, String name, Object version) {
        MavenHttpModule module = repo.module(group, name, version as String)
        module.allowAll()
    }

    def expectModule(MavenHttpRepository repo, String group, String name, String version) {
        MavenHttpModule module1 = repo.module(group, name, version).publish()
        module1.allowAll()
    }

    def withMavenBuild(String name) {
        resources.maybeCopy("${getClass().simpleName}/$name")
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
