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

package org.gradle.play.tasks

import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.deployment.internal.Deployment
import org.gradle.play.internal.run.PlayApplicationRunner
import org.gradle.play.internal.run.PlayApplicationRunnerToken
import org.gradle.play.internal.run.PlayRunSpec
import org.gradle.play.internal.toolchain.PlayToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class PlayRunTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    PlayToolProvider playToolProvider = Mock(PlayToolProvider)
    PlayApplicationRunner playApplicationRunner = Mock(PlayApplicationRunner)
    def address = InetSocketAddress.createUnresolved("localhost", 12345)

    PlayRun playRun

    def setup() {
        playRun = TestUtil.create(tmpDir).task(PlayRun)
        playRun.applicationJar = new File("application.jar")
        playRun.runtimeClasspath = new SimpleFileCollection()
        playRun.playToolProvider = playToolProvider
        runnerToken.isRunning() >> true
        runnerToken.playAppAddress >> address
    }

    def "can customize memory"() {
        given:
        playRun.forkOptions.memoryInitialSize = "1G"
        playRun.forkOptions.memoryMaximumSize = "5G"
        when:
        playRun.run()
        then:
        1 * playToolProvider.get(PlayApplicationRunner) >> playApplicationRunner
        1 * playApplicationRunner.start(_, _) >> { PlayRunSpec spec, Deployment deployment ->
            assert spec.getForkOptions().memoryInitialSize == "1G"
            assert spec.getForkOptions().memoryMaximumSize == "5G"
            runnerToken
        }
    }

    def "passes forkOptions never null"() {
        when:
        playRun.run()
        then:
        1 * playToolProvider.get(PlayApplicationRunner) >> playApplicationRunner
        1 * playApplicationRunner.start(_, _) >> { PlayRunSpec spec, Deployment deployment ->
            assert spec.getForkOptions() != null
            runnerToken
        }
    }
}
