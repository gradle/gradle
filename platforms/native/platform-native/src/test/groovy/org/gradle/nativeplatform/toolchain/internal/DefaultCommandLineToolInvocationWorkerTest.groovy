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

package org.gradle.nativeplatform.toolchain.internal

import org.gradle.internal.operations.BuildOperationFailure
import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.internal.ExecException
import spock.lang.Specification

class DefaultCommandLineToolInvocationWorkerTest extends Specification {
    def "throws exception when exec fails"() {
        given:
        def execAction = Mock(ExecAction)
        def execActionFactory = Stub(ExecActionFactory) {
            newExecAction() >> execAction
        }
        execAction.getErrorOutput() >> _
        execAction.getStandardOutput() >> _

        def context = new DefaultMutableCommandLineToolContext()
        def executable = Mock(File)
        def commandLineTool = new DefaultCommandLineToolInvocationWorker("Tool", executable, execActionFactory)
        def invocation = new DefaultCommandLineToolInvocation("doing something", null, [], context, Mock(BuildOperationLogger))

        when:
        commandLineTool.execute(invocation, null)

        then:
        1 * execAction.executable(executable)
        1 * execAction.execute() >> { throw new ExecException("fail") }
        BuildOperationFailure e = thrown()
        e.getMessage().contains('Tool failed while doing something')

    }
}
