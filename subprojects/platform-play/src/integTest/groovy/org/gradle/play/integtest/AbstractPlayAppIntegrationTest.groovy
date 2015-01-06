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

package org.gradle.play.integtest

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.os.OperatingSystem
import org.gradle.play.integtest.fixtures.DistributionTestExecHandleBuilder
import org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.util.AvailablePortFinder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.UrlValidator.*

abstract class AbstractPlayAppIntegrationTest extends PlayMultiVersionIntegrationTest {
    int httpPort

    abstract PlayApp getPlayApp()
    def portFinder = AvailablePortFinder.createPrivate()

    def setup() {
        playApp.writeSources(file("."))
        settingsFile << """ rootProject.name = '${playApp.name}' """
        buildFile << """
model {
    components {
        play {
            targetPlatform "play-${version}"
        }
    }
}
"""
    }

    def "can build play app binary"() {
        when:
        succeeds("assemble")
        then:
        executedAndNotSkipped(
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":assemble")

        and:
        verifyJars()

        when:
        succeeds("createPlayBinaryJar")

        then:
        skipped(":createPlayBinaryJar", ":twirlCompilePlayBinary")

        when:
        succeeds("dist")

        then:
        executedAndNotSkipped(
                ":createPlayBinaryStartScripts",
                ":createPlayBinaryDist")
        skipped(
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar")

        and:
        verifyZips()

        when:
        succeeds("stage")

        then:
        executedAndNotSkipped(":stagePlayBinaryDist")
        skipped(
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":createPlayBinaryStartScripts")

        and:
        verifyStaged()
    }

    def "can run play app tests"() {
        setup:
        int testPort = portFinder.nextAvailable
        buildFile << """
        model {
            tasks.testPlayBinary {
                systemProperty 'testserver.port', $testPort
            }
        }
        """

        when:
        succeeds("check")
        then:
        executed(
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":scalaCompilePlayBinary",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":compilePlayBinaryTests",
                ":testPlayBinary")

        then:
        verifyTestOutput(new JUnitXmlTestExecutionResult(testDirectory, "build/playBinary/reports/test/xml"))

        when:
        succeeds("check")
        then:
        skipped(
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":scalaCompilePlayBinary",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":compilePlayBinaryTests",
                ":testPlayBinary")
    }

    def "can run play app"(){
        setup:
        httpPort = portFinder.nextAvailable

        buildFile <<
        """
        model {
            tasks.runPlayBinary {
                httpPort = $httpPort
            }
        }"""
        run "assemble"

        when:
        def userInput = new PipedOutputStream();
        executer.withStdIn(new PipedInputStream(userInput))
        GradleHandle gradleHandle = executer.withTasks(":runPlayBinary").start()

        then:
        def url = playUrl().toString()
        available(url, "Play app", 60000)
        assert playUrl().text.contains("Your new application is ready.")

        and:
        verifyContent()

        when: "stopping gradle"
        userInput.write(4) // ctrl+d
        userInput.write(TextUtil.toPlatformLineSeparators("\n").bytes) // For some reason flush() doesn't get the keystroke to the DaemonExecuter

        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        notAvailable(url)
    }

    @Requires(TestPrecondition.NOT_UNKNOWN_OS)
    @Unroll
    def "can run #type play distribution" () {
        ExecHandleBuilder builder
        ExecHandle handle
        String distDirPath = new File(testDirectory, distDirName).path
        buildFile << """
            model {
                tasks {
                    create("unzipDist", Copy) {
                        from (zipTree(tasks.createPlayBinaryDist.archivePath))
                        into "${distDirName}"
                        dependsOn tasks.createPlayBinaryDist
                    }
                }
            }
        """

        setup:
        httpPort = portFinder.nextAvailable
        run "${task}"
        if (OperatingSystem.current().unix) {
            assert file("${distDirName}/playBinary/bin/playBinary").mode == 0755
        }

        when:
        builder = new DistributionTestExecHandleBuilder(httpPort.toString(), distDirPath)
        handle = builder.build()
        handle.start()

        then:
        available(playUrl().toString(), "Play app", 60000)
        assert playUrl().text.contains("Your new application is ready.")

        and:
        verifyContent()

        cleanup:
        if (handle != null) {
            try {
                handle.abort()
            } catch (IllegalStateException e) {
                // Ignore if process is already not running
                println "Did not abort play process since current state is: ${handle.state.toString()}"
            }
        }

        where:
        type     | task        | distDirName
        "staged" | "stage"     | "build/stage"
        "zipped" | "unzipDist" | "build/dist"
    }

    void verifyJars() {
        jar("build/playBinary/lib/${playApp.name}.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "application.conf")
        jar("build/playBinary/lib/${playApp.name}-assets.jar").containsDescendants(
                "public/images/favicon.svg",
                "public/stylesheets/main.css",
                "public/javascripts/hello.js")
    }

    void verifyZips() {
        zip("build/distributions/playBinary.zip").containsDescendants(
                "playBinary/lib/${playApp.name}.jar",
                "playBinary/lib/${playApp.name}-assets.jar",
                "playBinary/bin/playBinary",
                "playBinary/bin/playBinary.bat",
                "playBinary/conf/application.conf",
                "playBinary/README"
        )
    }

    void verifyStaged() {
        stagedFilesExist(
                "lib/${playApp.name}.jar",
                "lib/${playApp.name}-assets.jar",
                "bin/playBinary",
                "bin/playBinary.bat",
                "conf/application.conf",
                "README")
    }

    // TODO:DAZ Use TestFile.assertContainsDescendants()
    void stagedFilesExist(String... files) {
        files.each { fileName ->
            assert file("build/stage/playBinary/${fileName}").exists()
        }
    }

    void verifyContent() {
        // Check all static assets from the shared content
        assertUrlContent playUrl("assets/stylesheets/main.css"), file("public/stylesheets/main.css")
        assertUrlContent playUrl("assets/javascripts/hello.js"), file("public/javascripts/hello.js")
        assertBinaryUrlContent playUrl("assets/images/favicon.svg"), file("public/images/favicon.svg")
    }

    URL playUrl(String path='') {
        return new URL("http://localhost:$httpPort/${path}")
    }

    void verifyTestOutput(TestExecutionResult result) {
    }
}
