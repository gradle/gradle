/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.provider.sources.process

import org.gradle.api.Action
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.ValueSourceBasedSpec
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

import java.nio.charset.Charset

class ProcessOutputValueSourceTest extends ValueSourceBasedSpec {
    def specFactory = TestFiles.execActionFactory()

    def "command line is propagated to execOperations"() {
        given:
        def spec = specFactory.newExecAction()

        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                withDefaultEnvironment(it)
            }
        }.get()

        then:
        1 * execOperations.exec(_) >> { Action<? super ExecSpec> action -> action.execute(spec) }
        spec.getCommandLine() == ["echo", "hello"]
        spec.environment == System.getenv()
    }

    def "full environment is propagated to execOperations"() {
        given:
        def spec = specFactory.newExecAction()

        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                withFullEnvironment(it, [FOO: "BAR"])
            }
        }.get()

        then:
        1 * execOperations.exec(_) >> { Action<? super ExecSpec> action -> action.execute(spec) }
        spec.environment == [FOO: "BAR"]
    }

    def "additional environment is propagated to execOperations"() {
        given:
        def spec = specFactory.newExecAction()

        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                withAdditionalEnvironment(it, [FOO: "BAR"])
            }
        }.get()

        then:
        1 * execOperations.exec(_) >> { Action<? super ExecSpec> action -> action.execute(spec) }
        spec.environment == System.getenv() + [FOO: "BAR"]
    }

    def "specifying both full and additional environment is an error"() {
        given:
        def spec = specFactory.newExecAction()
        execOperations.exec(_ as Action<? super ExecSpec>) >> { Action<? super ExecSpec> action -> action.execute(spec) }

        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                it.fullEnvironment = [FOO: "BAR"]
                it.additionalEnvironmentVariables = [BAR: "BAZ"]
            }
        }.get()

        then:
        def e = thrown ObjectInstantiationException
        e.getCause() instanceof IllegalArgumentException
    }

    def "ignoreReturnValue is false by default"() {
        given:
        def spec = specFactory.newExecAction()

        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                withDefaultEnvironment(it)
            }
        }.get()

        then:
        1 * execOperations.exec(_) >> { Action<? super ExecSpec> action -> action.execute(spec) }
        !spec.ignoreExitValue.get()
    }

    def "ignoreReturnValue is propagated to execOperations"() {
        given:
        def spec = specFactory.newExecAction()

        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                it.ignoreExitValue = true
                withDefaultEnvironment(it)
            }
        }.get()

        then:
        1 * execOperations.exec(_) >> { Action<? super ExecSpec> action -> action.execute(spec) }
        spec.ignoreExitValue.get()
    }

    def "execution result is available from the provider"() {
        given:
        execOperations.exec(_ as Action<? super ExecSpec>) >> { Action<? super ExecSpec> action ->
            def spec = specFactory.newExecAction()
            action.execute(spec)
            spec.standardOutput.get().write("output".getBytes(Charset.defaultCharset()))
            spec.standardOutput.get().close()
            spec.errorOutput.get().write("error".getBytes(Charset.defaultCharset()))
            spec.errorOutput.get().close()
            return Stub(ExecResult) {
                getExitValue() >> 0
            }
        }

        when:
        def provider = createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                withDefaultEnvironment(it)
            }
        }
        def result = provider.get()

        then:
        result.getResult().getExitValue() == 0
        new String(result.getOutput(), Charset.defaultCharset()) == "output"
        new String(result.getError(), Charset.defaultCharset()) == "error"
    }

    def "execOperations are not called if provider is not queried"() {
        when:
        createProviderOf(ProcessOutputValueSource.class) {
            it.parameters {
                it.commandLine = ["echo", "hello"]
                withDefaultEnvironment(it)
            }
        }

        then:
        0 * execOperations._
    }

    private static void withDefaultEnvironment(ProcessOutputValueSource.Parameters parameters) {
        // Properties of parameters of the ValueSource are already present at the moment of creation,
        // so we have to unset them explicitly here.
        parameters.fullEnvironment = null
        parameters.additionalEnvironmentVariables = null
    }

    private static void withFullEnvironment(ProcessOutputValueSource.Parameters parameters, Map<String, Object> environment) {
        parameters.fullEnvironment = environment
        parameters.additionalEnvironmentVariables = null
    }

    private static void withAdditionalEnvironment(ProcessOutputValueSource.Parameters parameters, Map<String, Object> environment) {
        parameters.fullEnvironment = null
        parameters.additionalEnvironmentVariables = environment
    }
}
