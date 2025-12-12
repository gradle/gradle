/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.Help
import spock.lang.Specification

class VersionHelpConsumerActionExecutorTest extends Specification {
    def delegate = Mock(ConsumerActionExecutor)
    def executor = new VersionHelpConsumerActionExecutor(delegate)
    def action = Mock(ConsumerAction)
    def output = new ByteArrayOutputStream()

    def createParameters(List<String> args) {
        return ConsumerOperationParameters.builder()
            .setEntryPoint("entryPoint")
            .setParameters(Mock(ConnectionParameters))
            .setStdout(output)
            .setArguments(args)
            .build()
    }

    def "delegates when no help or version arguments"() {
        given:
        def parameters = createParameters(["task"])
        action.parameters >> parameters

        when:
        executor.run(action)

        then:
        1 * delegate.run(action)
    }

    def "prints help and short-circuits when --help is present"() {
        given:
        def parameters = createParameters(["--help"])
        action.parameters >> parameters
        def helpModel = Mock(Help)
        helpModel.renderedText >> "Help output"

        when:
        executor.run(action)

        then:
        1 * delegate.run(_) >> helpModel
        output.toString("UTF-8") == "Help output"
        0 * delegate.run(action)
    }

    def "prints version and short-circuits when --version is present"() {
        given:
        def parameters = createParameters(["--version"])
        action.parameters >> parameters
        def env = Mock(BuildEnvironment)
        env.versionInfo >> "Version info"

        when:
        executor.run(action)

        then:
        1 * delegate.run(_) >> env
        output.toString("UTF-8") == "Version info"
        0 * delegate.run(action)
    }

    def "prints version and continues when --show-version is present"() {
        given:
        def parameters = createParameters(["--show-version", "task"])
        action.parameters >> parameters
        def env = Mock(BuildEnvironment)
        env.versionInfo >> "Version info"

        when:
        executor.run(action)

        then:
        1 * delegate.run({ ConsumerAction a -> a != action }) >> env
        output.toString("UTF-8") == "Version info"
        1 * delegate.run({ ConsumerAction a ->
            a != action && a.parameters.arguments == ["task"]
        })
    }
}
