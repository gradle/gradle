/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.play.integtest.fixtures.DistributionTestExecHandleBuilder
import org.gradle.play.integtest.fixtures.MultiProjectRunningPlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.play.integtest.fixtures.app.PlayMultiProject
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil

import static org.gradle.integtests.fixtures.UrlValidator.*

class PlayMultiProjectApplicationIntegrationTest extends AbstractIntegrationSpec {
    PlayApp playApp = new PlayMultiProject()
    RunningPlayApp runningApp = new MultiProjectRunningPlayApp(testDirectory)

    def setup() {
        playApp.writeSources(testDirectory)
    }

    def "can build play app binary"() {
        when:
        succeeds(":primary:assemble")

        then:
        executedAndNotSkipped(
                ":javalibrary:jar",
                ":submodule:playBinary",
                ":primary:playBinary",
                ":primary:assemble")

        and:
        jar("primary/build/playBinary/lib/primary.jar").containsDescendants(
                "Routes.class",
                "controllers/Application.class")
        jar("primary/build/playBinary/lib/primary-assets.jar").hasDescendants(
                "public/primary.txt")
        jar("submodule/build/playBinary/lib/submodule.jar").containsDescendants(
                "controllers/submodule/Application.class")
        jar("submodule/build/playBinary/lib/submodule-assets.jar").hasDescendants(
                "public/submodule.txt")

        when:
        succeeds(":primary:dist")

        then:
        zip("primary/build/distributions/playBinary.zip").containsDescendants(
                "playBinary/lib/primary.jar",
                "playBinary/lib/primary-assets.jar",
                "playBinary/lib/submodule.jar",
                "playBinary/lib/submodule-assets.jar",
                "playBinary/lib/javalibrary.jar",
                "playBinary/bin/playBinary",
                "playBinary/bin/playBinary.bat",
                "playBinary/conf/application.conf"
        )

        when:
        succeeds(":primary:stage")

        then:
        file("primary/build/stage/playBinary").assertIsDir().assertContainsDescendants(
                "lib/primary.jar",
                "lib/primary-assets.jar",
                "lib/submodule.jar",
                "lib/submodule-assets.jar",
                "bin/playBinary",
                "bin/playBinary.bat",
                "conf/application.conf"
        )
    }


    def "can run play app"(){
        setup:
        file("primary/build.gradle") << """
    model {
        tasks.runPlayBinary {
            httpPort = ${runningApp.selectPort()}
        }
    }
"""
        run ":primary:assemble"

        when:
        def userInput = new PipedOutputStream();
        executer.withStdIn(new PipedInputStream(userInput))
        GradleHandle gradleHandle = executer.withTasks(":primary:runPlayBinary").start()

        then:
        def url = runningApp.playUrl().toString()
        available(url, "Play app", 60)

        and:
        runningApp.verifyContent();

        when: "stopping gradle"
        userInput.write(4) // ctrl+d
        userInput.write(TextUtil.toPlatformLineSeparators("\n").bytes) // For some reason flush() doesn't get the keystroke to the DaemonExecuter

        gradleHandle.waitForFinish()

        then: "play server is stopped too"
        notAvailable(url)
    }

    @Requires(TestPrecondition.NOT_UNKNOWN_OS)
    def "can run play distribution" () {
        println file(".")

        ExecHandle handle
        String distDirPath = file("primary/build/stage").path

        setup:
        run ":primary:stage"

        when:
        ExecHandleBuilder builder = new DistributionTestExecHandleBuilder(runningApp.selectPort().toString(), distDirPath)
        handle = builder.build()
        handle.start()

        then:
        available(runningApp.playUrl().toString(), "Play app", 60)

        and:
        runningApp.verifyContent()

        cleanup:
        ((DistributionTestExecHandleBuilder.DistributionTestExecHandle) handle).shutdown()
        notAvailable(runningApp.playUrl().toString())
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    ZipTestFixture zip(String fileName) {
        new ZipTestFixture(file(fileName))
    }
}
