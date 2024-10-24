/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.process.internal.streams.EmptyStdInStreamsHandler
import org.gradle.process.internal.streams.ForwardStdinStreamsHandler
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import java.util.concurrent.Executor

@UsesNativeServices
class DefaultExecHandleBuilderTest extends Specification {
    private final DefaultExecHandleBuilder builder = new DefaultExecHandleBuilder(TestUtil.objectFactory(), TestFiles.pathToFileResolver(), Mock(Executor))

    def defaultsToEmptyStandardInput() {
        expect:
        builder.standardInput.get().read() < 0
        builder.executable("/bin/bash")
        builder.workingDir(new File(".").absoluteFile)
        builder.build().inputHandler instanceof EmptyStdInStreamsHandler
    }

    def canAttachAStandardInputStream() {
        def inputStream = new ByteArrayInputStream()

        when:
        builder.executable("/bin/bash")
        builder.workingDir(new File(".").absoluteFile)
        builder.standardInput.set(inputStream)

        then:
        builder.standardInput.get() == inputStream
        builder.build().inputHandler instanceof ForwardStdinStreamsHandler
    }

    def handlesCommandLineWithNoArgs() {
        when:
        builder.commandLine('command')

        then:
        builder.executable == 'command'
        builder.args == []
    }

    def convertsArgsToString() {
        when:
        builder.args(['1', 2, "${3}"])

        then:
        builder.args == ['1', '2', '3']
        builder.allArguments == ['1', '2', '3']
    }
}
