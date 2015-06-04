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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

@Timeout(120)
@Requires(TestPrecondition.JDK7_OR_LATER)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_RICH_PROGRESS_EVENTS)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
class PlayContinuousBuildCrossVersionSpec extends ContinuousBuildToolingApiSpecification {
    PlayApp playApp = new BasicPlayApp()
    RunningPlayApp runningApp = new RunningPlayApp(projectDir)
    int shutdownTimeout = 10

    def setup() {
        playApp.writeSources(projectDir)
        buildFile << """
            model {
                tasks.runPlayBinary {
                    httpPort = ${runningApp.selectPort()}
                }
            }
        """
        settingsFile << """
            rootProject.name = '${playApp.name}'
        """

        buildTimeout = 90
    }

    def "play application is stopped when continuous build is cancelled" () {
        when:
        runBuild(["runPlayBinary"]) {
            succeeds()
            appIsRunningAndDeployed()
            file("conf/routes") << "\n#a change"
            succeeds()
        }

        then:
        appIsStopped()
    }

    def appIsRunningAndDeployed() {
        runningApp.verifyStarted()
        runningApp.verifyContent()
        true
    }

    def appIsStopped() {
        new PollingConditions().within(shutdownTimeout) {
            runningApp.verifyStopped()
        }
        true
    }
}
