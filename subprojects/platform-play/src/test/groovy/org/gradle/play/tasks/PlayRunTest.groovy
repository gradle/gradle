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

import org.gradle.play.internal.run.PlayApplicationRunner
import org.gradle.play.internal.run.PlayApplicationRunnerToken
import org.gradle.play.internal.run.PlayRunSpec
import org.gradle.play.internal.toolchain.PlayToolChainInternal
import org.gradle.play.internal.toolchain.PlayToolProvider
import org.gradle.play.platform.PlayPlatform
import org.gradle.util.RedirectStdIn
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class PlayRunTest extends Specification {

    PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    PlayApplicationRunner playApplicationRunner = Mock(PlayApplicationRunner)
    PlayToolChainInternal toolChain = Mock(PlayToolChainInternal)
    PlayPlatform playPlatform = Mock(PlayPlatform)
    PlayToolProvider toolProvider = Mock()
    InputStream systemInputStream = Mock()

    @Rule RedirectStdIn redirectStdIn;

    PlayRun playRun

    def setup(){
        playRun = TestUtil.createTask(PlayRun, [__toolChain__: toolChain])
        playRun.applicationJar = new File("application.jar")

        _ * playPlatform.playVersion >> "2.2.3"
        _ * playPlatform.scalaMainVersion >> "2.10"
        1 * toolChain.select(playPlatform) >> toolProvider
        playRun.targetPlatform = playPlatform
        _ * playApplicationRunner.start() >> runnerToken

        System.in = systemInputStream
    }

    def "can customize memory"(){
        given:
        1 * systemInputStream.read() >> 4
        playRun.forkOptions.memoryInitialSize = "1G"
        playRun.forkOptions.memoryMaximumSize = "5G"
        when:
        playRun.execute();
        then:
        1 * toolProvider.newApplicationRunner(_, _) >> {factory, PlayRunSpec spec ->
            assert spec.getForkOptions().memoryInitialSize == "1G"
            assert spec.getForkOptions().memoryMaximumSize == "5G"
            playApplicationRunner
        }
    }

    def "passes forkOptions never null"() {
        1 * systemInputStream.read() >> 4
        when:
        playRun.execute();
        then:
        1 * toolProvider.newApplicationRunner(_, _) >> { factory, PlayRunSpec spec ->
            assert spec.getForkOptions() != null
            playApplicationRunner
        }
    }

    def "stops application after receiving ctrl+d"(){
        1 * systemInputStream.read() >> {
            1 * runnerToken.stop()
            return 4
        }
        when:
        playRun.execute();
        then:
        1 * toolProvider.newApplicationRunner(_, _) >> playApplicationRunner
    }
}
