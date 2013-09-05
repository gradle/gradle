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

package org.gradle.buildsetup.plugins

import org.gradle.buildsetup.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenHttpModule
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.maven.PomHttpArtifact
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class MavenConversionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Rule
    public final SetSystemProperties systemProperties = new SetSystemProperties()

    def "multiModule"() {
        when:
        run 'init'

        then:
        settingsFile.exists()
        buildFile.exists()
        wrapperFilesGenerated()

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
        output.contains(toPlatformLineSeparators("""
Root project 'webinar-parent'
+--- Project ':webinar-api' - Webinar APIs
+--- Project ':webinar-impl' - Webinar implementation
\\--- Project ':webinar-war' - Webinar web application
"""))
    }

    def "flatmultimodule"() {
        when:
        executer.inDirectory(file("webinar-parent"))
        run 'init'

        then:
        file("webinar-parent/settings.gradle").exists()
        file("webinar-parent/build.gradle").exists()
        wrapperFilesGenerated(file("webinar-parent"))

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
        output.contains(toPlatformLineSeparators("""
Root project 'webinar-parent'
+--- Project ':webinar-api' - Webinar APIs
+--- Project ':webinar-impl' - Webinar implementation
\\--- Project ':webinar-war' - Webinar web application
"""))
    }

    def "singleModule"() {
        when:
        run 'init'

        then:
        buildFile.exists()
        settingsFile.exists()
        wrapperFilesGenerated()

        when:
        //TODO this build should fail because the TestNG test is failing
        //however the plugin does not generate testNG for single module project atm (bug)
        //def failure = runAndFail('clean', 'build')  //assert if fails for the right reason
        run 'clean', 'build'
        then:
        file("build/libs/util-2.5.jar").exists()
    }

    def "testjar"() {
        when:
        run 'init'

        then:
        settingsFile.exists()
        settingsFile.exists()
        wrapperFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/testjar-2.5.jar").exists()
        file("build/libs/testjar-2.5-tests.jar").exists()
    }

    def "enforcerplugin"() {
        when:
        run 'init'

        then:
        settingsFile.exists()
        buildFile.exists()
        wrapperFilesGenerated()

        and:
        buildFile.text.contains("""configurations.all {
it.exclude group: 'org.apache.maven'
it.exclude group: 'org.apache.maven', module: 'badArtifact'
it.exclude group: '*', module: 'badArtifact'
}""")
        when:
        run 'clean', 'build'

        then:
        file("build/libs/enforcerExample-1.0.jar").exists()
    }

    def "providedNotWar"() {
        when:
        run 'init'

        then:
        settingsFile.exists()
        buildFile.exists()
        wrapperFilesGenerated()

        when:
        run 'clean', 'build'

        then:
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
        settingsFile.exists()
        buildFile.exists()
        wrapperFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/testApp-1.0.jar").exists()
    }

    @Issue("GRADLE-2820")
    def "remoteparent"() {
        setup:
        def repo = setupMavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())

        expectParentPomRequest(repo)
        withLocalM2Installation()

        when:
        run 'init'

        then:
        buildFile.exists()
        settingsFile.exists()
        wrapperFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/util-2.5.jar").exists()
    }

    @Issue("GRADLE-2872")
    def "expandProperties"() {
        setup:
        String module1Version = "1.0"
        String module2Version = "2.0"
        String module3Version = "3.0"
        def repo = setupMavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())
        expectModule(repo, "group", "module1", module1Version);
        expectModule(repo, "group", "module2", module2Version);
        expectModule(repo, "group", "module3", module3Version);
        System.setProperty("MODULE1_VERSION", "1.0")
        withLocalM2Installation().globalSettingsFile.createFile().text = """
<settings>
    <profiles>
        <profile>
          <id>testprofile</id>
          <properties>
            <module3-version>3.0</module3-version>
          </properties>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>testprofile</activeProfile>
    </activeProfiles>
</settings>
"""

        when:
        run 'init'
        then:
        buildFile.exists()
        settingsFile.exists()
        wrapperFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/util-2.0.jar").exists()
    }

    def expectModule(MavenHttpRepository repo, String group, String name, String version) {
        MavenHttpModule module1 = repo.module(group, name, version).publish()
        module1.pom.expectHead()
        module1.pom.expectGet()
        module1.pom.sha1.expectGet()
        module1.artifact.expectHead()
        module1.artifact.sha1.expectGet()
        module1.artifact.expectGet()
    }

    @Issue("GRADLE-2819")
    def "multiModuleWithRemoteParent"() {
        setup:
        def repo = setupMavenHttpServer()
        //update pom with test repo url
        file("pom.xml").text = file("pom.xml").text.replaceAll('LOCAL_MAVEN_REPO_URL', repo.getUri().toString())

        expectParentPomRequest(repo)
        withLocalM2Installation()

        when:
        run 'init'

        then:
        buildFile.exists()
        settingsFile.exists()
        wrapperFilesGenerated()

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
        output.contains(toPlatformLineSeparators("""
Root project 'webinar-parent'
+--- Project ':webinar-api' - Webinar APIs
+--- Project ':webinar-impl' - Webinar implementation
\\--- Project ':webinar-war' - Webinar web application
"""))
    }

    M2Installation withLocalM2Installation() {
        M2Installation m2Installation = new M2Installation(testDirectory)
        m2Installation.generateUserSettingsFile(maven("local_m2"))
        using m2Installation
        m2Installation
    }

    PomHttpArtifact expectParentPomRequest(MavenHttpRepository repo) {
        MavenHttpModule module = repo.module('util.util.parent', 'util-parent', '3')
        module.pom.file.parentFile.mkdirs()
        module.pom.file.text = """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
                    \t<modelVersion>4.0.0</modelVersion>
                        <groupId>util.util.parent</groupId>
                        <artifactId>util-parent</artifactId>
                        <version>3</version>
                        <packaging>pom</packaging>
                    \t<name>Test Parent Pom</name>
                        <description>Defaults for Test Maven Build with remote parent projects.</description>
                    </project>
                    """
        module.pom
        module.pom.expectGet();
        module.pom.sha1.expectGet();
        module.pom.md5.expectGet();
        module.pom
    }

    MavenHttpRepository setupMavenHttpServer() {
        HttpServer server = new HttpServer()
        server.start()
        new MavenHttpRepository(server, '/maven', maven(file("maven_remote_repo")));
    }

    void wrapperFilesGenerated(TestFile parentFolder = file(".")) {
        new WrapperTestFixture(parentFolder).generated()
    }
}