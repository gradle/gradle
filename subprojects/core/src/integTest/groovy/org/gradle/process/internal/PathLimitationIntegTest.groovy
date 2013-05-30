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
import org.gradle.CacheUsage
import org.gradle.api.Action
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.logging.LogLevel
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.*
import org.gradle.cache.internal.locklistener.NoOpFileLockListener
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeplatform.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.listener.ListenerBroadcast
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.dispatch.MethodInvocation
import org.gradle.messaging.remote.MessagingServer
import org.gradle.messaging.remote.ObjectConnection
import org.gradle.messaging.remote.internal.MessagingServices
import org.gradle.process.internal.child.WorkerProcessClassPathProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class PathLimitationIntegTest extends Specification {
    private final TestListenerInterface listenerMock = Mock(TestListenerInterface.class);
    private final MessagingServices messagingServices = new MessagingServices(getClass().getClassLoader());
    private final MessagingServer server = messagingServices.get(MessagingServer.class);

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final ProcessMetaDataProvider metaDataProvider = new DefaultProcessMetaDataProvider(NativeServices.getInstance().get(org.gradle.internal.nativeplatform.ProcessEnvironment.class));
    private final CacheFactory factory = new DefaultCacheFactory(new DefaultFileLockManager(metaDataProvider, new NoOpFileLockListener())).create();
    private final CacheRepository cacheRepository = new DefaultCacheRepository(tmpDir.getTestDirectory(), null, CacheUsage.ON, factory);
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry();
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry), new WorkerProcessClassPathProvider(cacheRepository, moduleRegistry));
    private final DefaultWorkerProcessFactory workerFactory = new DefaultWorkerProcessFactory(LogLevel.INFO, server, classPathRegistry, TestFiles.resolver(tmpDir.getTestDirectory()), new LongIdGenerator());
    private final ListenerBroadcast<TestListenerInterface> broadcast = new ListenerBroadcast<TestListenerInterface>(
            TestListenerInterface.class);
    private final RemoteExceptionListener exceptionListener = new RemoteExceptionListener(broadcast);

    public void setup() {
        broadcast.add(listenerMock);
    }

    public void after() {
        messagingServices.stop();
    }

    @IgnoreIf({OperatingSystem.current().isWindows()})
    @Unroll
    def "WorkerProcessBuilder handles workingDir with absolute path length #absolutePathLength"() throws Throwable {
        when:
        def testWorkingDir = generateTestWorkingDirectory(absolutePathLength)
        then:
        assert testWorkingDir.exists()
        execute(worker(new HelloWorldRemoteProcess(), testWorkingDir))
        where:
        absolutePathLength << [258, 259, 260]
    }

    @IgnoreIf({OperatingSystem.current().isWindows()})
    @Unroll
    def "JavaProcessBuilder handles workingDir with absolute path length #absolutePathLength"() throws Throwable {
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
        process.errorStream.text.contains("java version")

        where:
        absolutePathLength << [258, 259, 260]
    }

    @Ignore
    @Unroll
    def "OS handles workingDir with absolute path length #absolutePathLength"() throws Throwable {
        setup:
        def testWorkingDir = generateTestWorkingDirectory(absolutePathLength)
        TestFile testBatchScript = tmpDir.getTestDirectory().createFile("testBatch.cmd")
        testBatchScript.text = """
        cd ${testWorkingDir.name}
        java -version > o.txt 2>&1
"""

        when:

        assert testWorkingDir.exists()
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(tmpDir.getTestDirectory())
        processBuilder.command("CMD", "/C", "testBatch.cmd")

        and:
        Process process = processBuilder.start()

        then:
        process.waitFor() == 0
        process.exitValue() == 0
        and:
        def outputText = new File(testWorkingDir, "o.txt").text
        println outputText
        assert outputText.contains("java version")
        where:
        absolutePathLength << [250, 255, 260] // 250 succeeds
                                              // 255 fails because path + "/o.txt" >= 260
                                              // 260 fails different because path >= 260
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
        messagingServices.stop();
        exceptionListener.rethrow();
    }

    private class ChildProcess {
        private boolean stopFails;
        private boolean startFails;
        private WorkerProcess proc;
        private Action<? super WorkerProcessContext> action;
        private List<String> jvmArgs = Collections.emptyList();
        private Action<ObjectConnection> serverAction;
        private final File workingDirectory

        public ChildProcess(Action<? super WorkerProcessContext> action, File workingDirectory) {
            this.workingDirectory = workingDirectory
            this.action = action;
        }

        ChildProcess expectStopFailure() {
            stopFails = true;
            return this;
        }

        ChildProcess expectStartFailure() {
            startFails = true;
            return this;
        }

        public void start() {
            WorkerProcessBuilder builder = workerFactory.create();
            builder.worker(action);
            builder.getJavaCommand().jvmArgs(jvmArgs);
            builder.getJavaCommand().setWorkingDir(workingDirectory)

            proc = builder.build();
            try {
                proc.start();
                assertFalse(startFails);
            } catch (ExecException e) {
                println e
                e.printStackTrace()
                assertTrue(startFails);
                return;
            }
            proc.getConnection().addIncoming(TestListenerInterface.class, exceptionListener);
            if (serverAction != null) {
                serverAction.execute(proc.getConnection());
            }
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

        public ChildProcess onServer(Action<ObjectConnection> action) {
            this.serverAction = action;
            return this;
        }

        public ChildProcess jvmArgs(String... jvmArgs) {
            this.jvmArgs = Arrays.asList(jvmArgs);
            return this;
        }
    }


    public static class RemoteExceptionListener implements Dispatch<MethodInvocation> {
        Throwable ex;
        final Dispatch<MethodInvocation> dispatch;

        public RemoteExceptionListener(Dispatch<MethodInvocation> dispatch) {
            this.dispatch = dispatch;
        }

        public void dispatch(MethodInvocation message) {
            try {
                dispatch.dispatch(message);
            } catch (Throwable e) {
                ex = e;
            }
        }

        public void rethrow() throws Throwable {
            if (ex != null) {
                throw ex;
            }
        }
    }

    public interface TestListenerInterface {
        public void send(String message, int count);
    }
}
