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
import org.gradle.api.file.FileCollection
import org.gradle.play.internal.run.PlayApplicationRunnerToken
import org.gradle.play.internal.run.PlayRunSpec
import org.gradle.play.internal.run.PlayApplicationRunner
import org.gradle.play.internal.toolchain.PlayToolChainInternal
import org.gradle.play.platform.PlayPlatform
import org.gradle.util.TestUtil
import spock.lang.Specification

class PlayRunTest extends Specification {

    PlayApplicationRunnerToken runnerToken = Mock(PlayApplicationRunnerToken)
    PlayApplicationRunner playApplicationRunner = Mock(PlayApplicationRunner)
    PlayToolChainInternal toolChain = Mock(PlayToolChainInternal)
    PlayPlatform playPlatform = Mock(PlayPlatform)

    PlayRun playRun

    def setup(){
        playRun = TestUtil.createTask(PlayRun, [__toolChain__: toolChain])
        playRun.classpath = Mock(FileCollection)

        _ * playPlatform.playVersion >> "2.2.3"
        _ * playPlatform.scalaVersion >> "2.10"

        playRun.targetPlatform = playPlatform

        _ * playApplicationRunner.start() >> runnerToken

    }

    def "can customize memory"(){
        given:
        playRun.forkOptions.memoryInitialSize = "1G"
        playRun.forkOptions.memoryMaximumSize = "5G"
        when:
        playRun.execute();
        then:
        1 * toolChain.createPlayApplicationRunner(_, _, _) >> {factory, platform, PlayRunSpec spec ->
            assert spec.getForkOptions().memoryInitialSize == "1G"
            assert spec.getForkOptions().memoryMaximumSize == "5G"
            playApplicationRunner
        }
    }

    def "waits for runner to stop"(){
        when:
        playRun.execute();
        then:
        1 * toolChain.createPlayApplicationRunner(_, _, _) >> playApplicationRunner
        1 * runnerToken.waitForStop()

    }
}
