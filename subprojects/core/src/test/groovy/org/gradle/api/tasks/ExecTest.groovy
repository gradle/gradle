/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.api.internal.AbstractTask
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecException

class ExecTest extends AbstractTaskTest {
    Exec execTask
    def execAction = Mock(ExecAction) {
        getArgumentProviders() >> []
    }

    def setup() {
        execTask = createTask(Exec.class)
        execTask.setExecAction(execAction)
    }

    AbstractTask getTask() {
        return execTask
    }

    def "executes action on execute"() {
        when:
        execTask.setExecutable("ls")
        execute(execTask)

        then:
        1 * execAction.setExecutable("ls")
        1 * execAction.execute() >> new ExpectedExecResult(0)
        execTask.execResult.exitValue == 0
        execTask.executionResult.get().exitValue == 0
    }

    def "execute with non-zero exit value and ignore exit value should not throw exception"() {
        when:
        execute(execTask)

        then:
        1 * execAction.execute() >> new ExpectedExecResult(1)
        execTask.execResult.exitValue == 1
        execTask.executionResult.get().exitValue == 1
    }

    private class ExpectedExecResult implements ExecResult {
        int exitValue

        ExpectedExecResult(int exitValue) {
            this.exitValue = exitValue
        }

        @Override
        int getExitValue() {
            return exitValue
        }

        @Override
        ExecResult assertNormalExitValue() throws ExecException {
            return this
        }

        @Override
        ExecResult rethrowFailure() throws ExecException {
            return this
        }
    }
}
