/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.play.integtest.basic

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.play.integtest.fixtures.AbstractMultiVersionPlayContinuousBuildIntegrationTest
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.RunningPlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.internal.DefaultPlayPlatform
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle/issues/4622')
@TargetCoverage({ [DefaultPlayPlatform.DEFAULT_PLAY_VERSION] })
@IntegrationTestTimeout(600)
class Play26HttpsIntegrationTest extends AbstractMultiVersionPlayContinuousBuildIntegrationTest {
    RunningPlayApp runningApp = new RunningPlayApp(testDirectory)
    PlayApp playApp = new BasicPlayApp(versionNumber)

    def setup() {
        executer.withRepositoryMirrors()
    }

    @ToBeFixedForInstantExecution
    def 'can enable https.port'() {
        when:
        buildFile << '''
            tasks.withType(PlayRun) {
                forkOptions.jvmArgs = ["-Dhttps.port=0"]
            }
        '''

        then:
        succeeds "runPlayBinary"

        and:
        appIsRunningAndDeployed()

        and:
        runningApp.output().contains('Listening for HTTPS on')
    }
}
