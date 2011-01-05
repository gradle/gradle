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
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.process.internal.ExecAction
import org.gradle.process.ExecResult
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.assertThat

@RunWith (org.jmock.integration.junit4.JMock)
public class ExecTest extends AbstractTaskTest {
    JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    Exec execTask;
    ExecAction execAction = context.mock(ExecAction);

    @Before
    public void setUp() {
        super.setUp()
        execTask = createTask(Exec.class)
        execTask.setExecAction(execAction)
    }

    public AbstractTask getTask() {
        return execTask;
    }

    @Test void executesActionOnExecute() {
        context.checking {
            one(execAction).setExecutable("ls")
            one(execAction).execute(); will(returnValue({ 0 } as ExecResult))
        }
        execTask.setExecutable("ls")
        execTask.execute()
        assertThat(execTask.execResult.exitValue, Matchers.equalTo(0))
    }

    @Test
    void executeWithNonZeroExitValueAndIgnoreExitValueShouldNotThrowException() {
        context.checking {
            one(execAction).execute(); will(returnValue({ 1 } as ExecResult))
        }
        execTask.execute()
        assertThat(execTask.execResult.exitValue, Matchers.equalTo(1))
    }


}