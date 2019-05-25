/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout

@IntegrationTestTimeout(180)
class ErrorInWorkerSocketIntegrationTest extends AbstractIntegrationSpec {
    private static final String MESSAGE = 'This breaks socket connection threads in worker process deliberately'

    def "worker won't hang when error occurs in socket connection"() {
        given:
        requireOwnGradleUserHomeDir()
        executer.getGradleUserHomeDir().file()

        file('buildSrc/src/main/java/Param.java') << """
import java.io.*;
public class Param implements Serializable {
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        throw new IllegalStateException("$MESSAGE");
    }
}
"""

        file('buildSrc/src/main/java/TestWorkerProcess.java') << '''
import org.gradle.api.Action;
import org.gradle.process.internal.worker.WorkerControl;
public interface TestWorkerProcess extends Action, WorkerControl {}
'''
        file('buildSrc/src/main/java/TestWorkerProcessImpl.java') << '''
import org.gradle.process.ExecResult;
import org.gradle.process.internal.worker.WorkerProcess;

public class TestWorkerProcessImpl implements TestWorkerProcess {
    public WorkerProcess start() { return null; }
    public ExecResult stop() { return null; }
    public void execute(Object param) { } 
}   
'''
        buildFile << '''
import org.gradle.process.internal.worker.WorkerProcessFactory
import org.gradle.workers.internal.WorkerDaemonProcess
import org.gradle.workers.internal.WorkerProtocol
import org.gradle.workers.internal.WorkerDaemonServer

task runBrokenWorker {
    doLast {
        WorkerProcessFactory workerProcessFactory = rootProject.services.get(WorkerProcessFactory)
        def builder = workerProcessFactory.multiRequestWorker(TestWorkerProcess, Action, TestWorkerProcessImpl)
        builder.getJavaCommand().setWorkingDir(project.rootDir)

        def workerDaemonProcess = builder.build()
        workerDaemonProcess.start()
        workerDaemonProcess.execute(new Param())
    }
}
'''
        when:
        fails('runBrokenWorker')

        then:
        failureCauseContains('No response was received from Gradle Worker but the worker process has finished')
        executer.getGradleUserHomeDir().file('workers').listFiles().find { it.name.startsWith('worker-error') }.text.contains(MESSAGE)
    }
}
