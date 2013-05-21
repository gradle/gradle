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
import org.junit.Rule

import static org.gradle.util.TextUtil.toPlatformLineSeparators

/**
 * by Szczepan Faber, created at: 9/4/12
 */
class MavenConversionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def "multiModule"() {
        when:
        run 'setupBuild'

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
        run 'setupBuild'

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
        run 'setupBuild'

        then:
        buildFile.exists()
        settingsFile.exists()
        wrapperFilesGenerated()

        when:
        //TODO SF this build should fail because the TestNG test is failing
        //however the plugin does not generate testNG for single module project atm (bug)
        //def failure = runAndFail('clean', 'build')  //assert if fails for the right reason
        run 'clean', 'build'

        then:
        file("build/libs/util-2.5.jar").exists()
    }

    def "testjar"() {
        when:
        run 'setupBuild'

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
        run 'setupBuild'

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
        run 'setupBuild'

        then:
        settingsFile.exists()
        buildFile.exists()
        wrapperFilesGenerated()

        when:
        run 'clean', 'build'

        then:
        file("build/libs/myThing-0.0.1-SNAPSHOT.jar").exists()
    }

    def "provides decent error message"() {
        setup:
        file("pom.xml") << "<project>someInvalid pom content</project>"
        when:
        fails 'setupBuild'

        then:
        errorOutput.contains("Failed to convert Maven project.")
    }


    void wrapperFilesGenerated(TestFile parentFolder = file(".")) {
        new WrapperTestFixture(parentFolder).generated()
    }
}