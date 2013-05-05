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

import org.gradle.process.ExecResult
import org.gradle.process.internal.streams.StreamsHandler
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GUtil
import org.gradle.util.Jvm
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Callable

/**
 * @author Tom Eyckmans, Szczepan Faber
 */
@Timeout(60)
class DefaultExecHandleSpec extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    void "forks process"() {
        given:
        def out = new ByteArrayOutputStream();
        def err = new ByteArrayOutputStream();

        def execHandle = handle()
                .args(args(TestApp.class, "arg1", "arg2"))
                .setStandardOutput(out)
                .setErrorOutput(err)
                .build();

        when:
        def result = execHandle.start().waitForFinish();

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
        result.exitValue == 0
        out.toString() == "output args: [arg1, arg2]"
        err.toString() == "error args: [arg1, arg2]"
        result.assertNormalExitValue()
    }

    void "waiting for process returns quickly if process already completed"() {
        given:
        def execHandle = handle()
                .args(args(TestApp.class))
                .build();

        def handle = execHandle.start()

        when:
        handle.waitForFinish();
        handle.waitForFinish();

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
    }

    void "understands when application exits with non-zero"() {
        given:
        def execHandle = handle().args(args(BrokenApp.class)).build();

        when:
        def result = execHandle.start().waitForFinish();

        then:
        execHandle.state == ExecHandleState.FAILED
        result.exitValue == 72

        when:
        result.assertNormalExitValue();

        then:
        def e = thrown(ExecException)
        e.message.contains "finished with non-zero exit value 72"
    }

    void "start fails when process cannot be started"() {
        def execHandle = handle().setDisplayName("awesome").executable("no_such_command").build();

        when:
        execHandle.start();

        then:
        def e = thrown(ExecException)
        e.message == "A problem occurred starting process 'awesome'"
    }

    void "aborts process"() {
        def execHandle = handle().args(args(SlowApp.class)).build();

        when:
        execHandle.start();
        execHandle.abort();
        def result = execHandle.waitForFinish();

        then:
        execHandle.state == ExecHandleState.ABORTED
        result.exitValue != 0
    }

    void "clients can listen to notifications"() {
        ExecHandleListener listener = Mock()
        def execHandle = handle().listener(listener).args(args(TestApp.class)).build();

        when:
        execHandle.start();
        execHandle.waitForFinish()

        then:
        1 * listener.executionStarted(execHandle)
        1 * listener.executionFinished(execHandle, _ as ExecResult)
        0 * listener._
    }

    void "forks daemon and aborts it"() {
        def output = new ByteArrayOutputStream()
        def execHandle = handle().setDaemon(true).setStandardOutput(output).args(args(SlowDaemonApp.class)).build();

        when:
        execHandle.start();
        execHandle.waitForFinish();

        then:
        output.toString().contains "I'm the daemon"
        execHandle.state == ExecHandleState.DETACHED

        cleanup:
        execHandle.abort()
    }

    @Ignore //TODO SF not yet implemented, as following @Ignores
    void "aborts daemon"() {
        def output = new ByteArrayOutputStream()
        def execHandle = handle().setDaemon(true).setStandardOutput(output).args(args(SlowDaemonApp.class)).build();

        when:
        execHandle.start();
        execHandle.waitForFinish();

        then:
        execHandle.state == ExecHandleState.DETACHED

        when:
        execHandle.abort()
        def result = execHandle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.ABORTED
        result.exitValue != 0
    }

    void "detaching does not trigger 'finished' notification"() {
        def out = new ByteArrayOutputStream()
        ExecHandleListener listener = Mock()
        def execHandle = handle().setDaemon(true).listener(listener).setStandardOutput(out).args(args(SlowDaemonApp.class)).build();

        when:
        execHandle.start();
        execHandle.waitForFinish();

        then:
        out.toString().contains "I'm the daemon"
        1 * listener.executionStarted(execHandle)
        0 * listener.executionFinished(_, _)

        cleanup:
        execHandle.abort()
    }

    @Ignore
    void "can detach from long daemon and then wait for finish"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().setStandardOutput(out).args(args(SlowDaemonApp.class, "200")).build();

        when:
        execHandle.start();
        execHandle.waitForFinish();

        then:
        out.toString().contains "I'm the daemon"

        when:
        execHandle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
    }

    @Ignore
    void "can detach from fast app then wait for finish"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().setStandardOutput(out).args(args(TestApp.class)).build();

        when:
        execHandle.start();
        execHandle.waitForFinish();
        execHandle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
    }

    @Ignore
    //it may not be easily testable
    void "detach detects when process did not start or died prematurely"() {
        def execHandle = handle().args(args(BrokenApp.class)).build();

        when:
        execHandle.start();
        def detachResult = execHandle.detach();

        then:
        execHandle.state == ExecHandleState.FAILED
        detachResult.processCompleted
        detachResult.execResult.exitValue == 72
    }

    void "can redirect error stream"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().args(args(TestApp.class)).setStandardOutput(out).redirectErrorStream().build();

        when:
        execHandle.start();
        execHandle.waitForFinish()

        then:
        ["output args", "error args"].each { out.toString().contains(it) }
    }

    void "exec handle collaborates with streams handler"() {
        given:
        def streamsHandler = Mock(StreamsHandler)
        def execHandle = handle().args(args(TestApp.class)).setDisplayName("foo proc").streamsHandler(streamsHandler).build();

        when:
        execHandle.start()
        def result = execHandle.waitForFinish()

        then:
        result.rethrowFailure()
        1 * streamsHandler.connectStreams(_ as Process, "foo proc")
        1 * streamsHandler.start()
        1 * streamsHandler.stop()
        0 * streamsHandler._
    }

    @Timeout(2)
    @Ignore
    void "exec handle can detach with timeout"() {
        given:
        def execHandle = handle().args(args(SlowApp.class)).setTimeout(1).build();

        when:
        execHandle.start()
        def result = execHandle.waitForFinish()

        then:
        result
        //the timeout does not hit
    }

    @Ignore
    void "exec handle can wait with timeout"() {
        given:
        def execHandle = handle().args(args(SlowApp.class)).setTimeout(1).build();

        when:
        execHandle.start()
        def result = execHandle.waitForFinish()

        then:
        result.exitValue != 0
        execHandle.state == ExecHandleState.ABORTED
    }

    static class Prints implements Callable, Serializable {

        String message

        Object call() {
            return message
        }
    }

    private ExecHandleBuilder handle() {
        new ExecHandleBuilder()
                .executable(Jvm.current().getJavaExecutable().getAbsolutePath())
                .setTimeout(20000) //sanity timeout
                .workingDir(tmpDir.getTestDirectory());
    }

    private List args(Class mainClass, String ... args) {
        GUtil.flattenElements("-cp", System.getProperty("java.class.path"), mainClass.getName(), args);
    }

    public static class TestApp {
        public static void main(String[] args) {
            System.out.print("output args: " + Arrays.asList(args));
            System.err.print("error args: " + Arrays.asList(args));
        }
    }

    public static class BrokenApp {
        public static void main(String[] args) {
            System.exit(72);
        }
    }

    public static class SlowApp {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(10000L);
        }
    }

    public static class SlowDaemonApp {
        public static void main(String[] args) throws InterruptedException {
            System.out.println("I'm the daemon");
            System.out.close();
            System.err.close();
            int napTime = (args.length == 0) ? 10000L : Integer.valueOf(args[0])
            Thread.sleep(napTime);
        }
    }

    public static class FastDaemonApp {
        public static void main(String[] args) throws InterruptedException {
            System.out.println("I'm the daemon");
            System.out.close();
            System.err.close();
        }
    }

    public static class InputReadingApp {
        public static void main(String[] args) throws InterruptedException {
            ObjectInputStream instr = new ObjectInputStream(System.in);
            Callable<?> main = (Callable<?>) instr.readObject();
            System.out.println(main.call())
        }
    }
}
