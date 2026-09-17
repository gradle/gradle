/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon.server.exec

import net.rubygrapefruit.platform.NativeException
import org.gradle.api.logging.Logger
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeintegration.EnvironmentModificationResult
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.launcher.daemon.protocol.Build
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.exec.BuildActionParameters
import spock.lang.Specification

class ApplyClientEnvironmentVariablesTest extends Specification {

    def execution = Mock(DaemonCommandExecution)
    def build = Mock(Build)
    def parameters = Mock(BuildActionParameters)
    def processEnvironment = Mock(ProcessEnvironment)
    def logger = Mock(Logger)
    def action = new ApplyClientEnvironmentVariables(processEnvironment, logger)

    def setup() {
        build.parameters >> parameters
        parameters.envVariables >> [FOO: "BAR"]
    }

    def "logs WARN when daemon cannot mutate its environment"() {
        given:
        processEnvironment.maybeSetEnvironment(_) >> EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT

        when:
        action.doBuild(execution, build)

        then:
        1 * logger.warn({ String message ->
            message.contains("Unable to set daemon's environment variables")
        })
    }

    def "does not log WARN when daemon successfully applies the environment"() {
        given:
        processEnvironment.maybeSetEnvironment(_) >> EnvironmentModificationResult.SUCCESS

        when:
        action.doBuild(execution, build)

        then:
        0 * logger.warn(_)
    }

    def "still applies the environment when the client environment matches this process"() {
        given:
        def matchingBuild = Mock(Build)
        def matchingParameters = Mock(BuildActionParameters)
        matchingBuild.parameters >> matchingParameters
        def matchingEnv = Jvm.getInheritableEnvironmentVariables(System.getenv())
        matchingParameters.envVariables >> matchingEnv

        when:
        action.doBuild(execution, matchingBuild)

        then: "the per-process variables the client filtered out are still scrubbed for the build"
        1 * execution.proceed()
        // Applied before the build and restored after it
        (1.._) * processEnvironment.maybeSetEnvironment(_) >> EnvironmentModificationResult.SUCCESS
        0 * logger.warn(_)
    }

    def "logs WARN instead of failing the build when mutating the environment throws"() {
        given:
        processEnvironment.maybeSetEnvironment(_) >> { throw new NativeException("Unable to get mutable environment variable map.") }

        when:
        action.doBuild(execution, build)

        then:
        1 * execution.proceed()
        1 * logger.warn({ String message ->
            message.contains("Unable to set daemon's environment variables")
        })
    }

}
