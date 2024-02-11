/*
 * Copyright 2013 the original author or authors.
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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.api.Action
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.jvm.Jvm
import org.gradle.process.internal.worker.WorkerProcess
import org.gradle.process.internal.worker.WorkerProcessBuilder
import org.gradle.process.internal.worker.WorkerProcessContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Timeout

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

@Timeout(120)
class PathLimitationIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    private final TestListenerInterface listenerMock = Mock(TestListenerInterface.class)
    private final ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(TestListenerInterface.class)
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(broadcast.source)

    public void setup() {
        broadcast.add(listenerMock)
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "WorkerProcessBuilder handles workingDir with absolute path length #absolutePathLength"() {
        when:
        def testWorkingDir = generateTestWorkingDirectory(absolutePathLength)
        then:
        assert testWorkingDir.exists()
        execute(worker(new HelloWorldRemoteProcess(), testWorkingDir))
        where:
        absolutePathLength << [258, 259, 260]
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "JavaProcessBuilder handles workingDir with absolute path length #absolutePathLength"() {
        when:
        def testWorkingDir = generateTestWorkingDirectory(absolutePathLength)

        assert testWorkingDir.exists()

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(testWorkingDir)

        processBuilder.command(Jvm.current().getJavaExecutable().absolutePath, "-version")
        then:
        Process process = processBuilder.start()

        process.waitFor() == 0
        process.exitValue() == 0
        and:
        process.errorStream.text.contains("version")

        where:
        absolutePathLength << [258, 259, 260]
    }

    TestFile generateTestWorkingDirectory(int absolutePathLength) {
        // windows can handle a path up to 260 characters (259 + NUL)
        // we create a path that is 260 +1 (absolutePathLength + "/" + randompath)
        def testDirectory = tmpDir.getTestDirectory()
        def pathoffset = absolutePathLength - 1 - testDirectory.getAbsolutePath().length()
        def alphanumeric = RandomStringUtils.randomAlphanumeric(pathoffset)
        return testDirectory.createDir("$alphanumeric")
    }

    public static class HelloWorldRemoteProcess implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            println "hello World!"
        }
    }

    private ChildProcess worker(Action<? super WorkerProcessContext> action, File workingDirectory = tmpDir.getTestDirectory()) {
        return new ChildProcess(action, workingDirectory);
    }

    void execute(ChildProcess... processes) throws Throwable {
        for (ChildProcess process : processes) {
            process.start();
        }
        for (ChildProcess process : processes) {
            process.waitForStop();
        }
        exceptionListener.rethrow();
    }

    private class ChildProcess {
        private boolean stopFails;
        private boolean startFails;
        private WorkerProcess proc;
        private Action<? super WorkerProcessContext> action;
        private List<String> jvmArgs = Collections.emptyList();
        private final File workingDirectory

        public ChildProcess(Action<? super WorkerProcessContext> action, File workingDirectory) {
            this.workingDirectory = workingDirectory
            this.action = action;
        }

        public void start() {
            WorkerProcessBuilder builder = workerFactory.create(action);
            builder.getJavaCommand().jvmArgs(jvmArgs);
            builder.getJavaCommand().setWorkingDir(workingDirectory)

            proc = builder.build();
            try {
                proc.start();
                assertFalse(startFails);
            } catch (ExecException e) {
                e.printStackTrace()
                assertTrue(startFails);
                return;
            }
            proc.getConnection().addIncoming(TestListenerInterface.class, exceptionListener);
            proc.getConnection().connect()
        }

        public void waitForStop() {
            if (startFails) {
                return;
            }
            try {
                proc.waitForStop();
                assertFalse("Expected process to fail", stopFails);
            } catch (ExecException e) {
                assertTrue("Unexpected failure in worker process", stopFails);
            }
        }

        public ChildProcess jvmArgs(String... jvmArgs) {
            this.jvmArgs = Arrays.asList(jvmArgs);
            return this;
        }
    }
}
