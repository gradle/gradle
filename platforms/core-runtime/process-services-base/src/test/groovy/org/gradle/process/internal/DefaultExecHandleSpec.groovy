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

import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.process.ExecResult
import org.gradle.process.ProcessExecutionException
import org.gradle.process.internal.streams.StreamsHandler
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Timeout

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@UsesNativeServices
@Timeout(60)
class DefaultExecHandleSpec extends AbstractExecHandleSpec {
    private final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    void "forks process"() {
        given:
        def out = new ByteArrayOutputStream()
        def err = new ByteArrayOutputStream()

        def execHandle = handle()
                .args(args(TestApp.class, "arg1", "arg2"))
                .setStandardOutput(out)
                .setErrorOutput(err)
                .build()

        when:
        def result = execHandle.start().waitForFinish()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
        result.exitValue == 0
        out.toString() == "output args: [arg1, arg2]"
        err.toString() == "error args: [arg1, arg2]"
        result.assertNormalExitValue()
        1 * buildCancellationToken.addCallback(_) >> {
            assert it[0].class == ExecHandleShutdownHookAction
            true
        }
        1 * buildCancellationToken.removeCallback(_) >> {
            assert it[0].class == ExecHandleShutdownHookAction
            true
        }
    }

    void "waiting for process returns quickly if process already completed"() {
        given:
        def execHandle = handle()
                .args(args(TestApp.class))
                .build()

        def handle = execHandle.start()

        when:
        handle.waitForFinish()
        handle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
    }

    void "understands when application exits with non-zero"() {
        given:
        def execHandle = handle().args(args(BrokenApp.class, "72")).build()

        when:
        def result = execHandle.start().waitForFinish()

        then:
        execHandle.state == ExecHandleState.FAILED
        result.exitValue == 72

        when:
        result.assertNormalExitValue()

        then:
        def e = thrown(ProcessExecutionException)
        e.message == "Process '${execHandle.displayName}' finished with non-zero exit value 72"
    }

    @Requires(OsTestPreconditions.Unix)
    void "provides detailed error message for processes terminated by a signal"() {
        given:
        def execHandle = handle().args(args(BrokenApp.class, exitCode.toString())).build()

        when:
        def result = execHandle.start().waitForFinish()

        then:
        execHandle.state == ExecHandleState.FAILED
        result.exitValue == exitCode

        when:
        result.assertNormalExitValue()

        then:
        def e = thrown(ProcessExecutionException)
        e.message == "Process '${execHandle.displayName}' finished with non-zero exit value $exitCode ($expectedMessage)"

        where:
        exitCode | expectedMessage
        137      | "this value may indicate that the process was terminated with the SIGKILL signal, which is often caused by the system running out of memory"
        143      | "this value may indicate that the process was terminated with the SIGTERM signal"
    }

    @Requires(OsTestPreconditions.Windows)
    void "provides detailed error message for processes terminated by the OS on Windows"() {
        given:
        def execHandle = handle().args(args(BrokenApp.class, exitCode.toString())).build()

        when:
        def result = execHandle.start().waitForFinish()

        then:
        execHandle.state == ExecHandleState.FAILED
        result.exitValue == exitCode

        when:
        result.assertNormalExitValue()

        then:
        def e = thrown(ProcessExecutionException)
        e.message == "Process '${execHandle.displayName}' finished with non-zero exit value $exitCode ($expectedMessage)"

        where:
        exitCode    | expectedMessage
        -1073741823 | "NTSTATUS 0xC0000001"
    }

    void "start fails when process cannot be started"() {
        def execHandle = handle().setDisplayName("awesome").setExecutable("no_such_command").build()

        when:
        execHandle.start()

        then:
        def e = thrown(ProcessExecutionException)
        e.message == "A problem occurred starting process 'awesome'"
    }

    void "aborts process"() {
        def execHandle = handle().args(args(SlowApp.class)).build()

        when:
        execHandle.start()

        then:
        1 * buildCancellationToken.addCallback(_) >> {
            assert it[0].class == ExecHandleShutdownHookAction
            true
        }

        when:
        execHandle.abort()

        then:
        1 * buildCancellationToken.removeCallback(_) >> {
            assert it[0].class == ExecHandleShutdownHookAction
            true
        }
        and:
        execHandle.state == ExecHandleState.ABORTED
        and:
        execHandle.waitForFinish().exitValue != 0
    }

    void "can abort after process has completed"() {
        given:
        def execHandle = handle().args(args(TestApp.class)).build()
        execHandle.start().waitForFinish()

        when:
        execHandle.abort()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED

        and:
        execHandle.waitForFinish().exitValue == 0
    }

    void "can abort after process has failed"() {
        given:
        def execHandle = handle().args(args(BrokenApp.class, "72")).build()
        execHandle.start().waitForFinish()

        when:
        execHandle.abort()

        then:
        execHandle.state == ExecHandleState.FAILED

        and:
        execHandle.waitForFinish().exitValue == 72
    }

    void "can abort after process has been aborted"() {
        given:
        def execHandle = handle().args(args(SlowApp.class)).build()
        execHandle.start()
        execHandle.abort()

        when:
        execHandle.abort()

        then:
        execHandle.state == ExecHandleState.ABORTED

        and:
        execHandle.waitForFinish().exitValue != 0
    }

    void "does not hang when the executor rejects the post-exit completion"() {
        given:
        def rejecting = new RejectingExecutor(executor)
        def execHandle = handle(rejecting).args(args(ShortApp.class)).build()

        when:
        execHandle.start()
        rejecting.process = execHandle.execHandleRunner.process
        execHandle.waitForFinish()

        then:
        // The executor is gone, so the completion can't run: the handle fails rather than hanging
        // (the class @Timeout guards against a regression to an indefinite block).
        thrown(ProcessExecutionException)
        execHandle.state == ExecHandleState.FAILED
    }

    void "does not hang when aborting and the executor rejects the post-exit completion"() {
        given:
        def rejecting = new RejectingExecutor(executor)
        def execHandle = handle(rejecting).args(args(SlowApp.class)).build()
        execHandle.start()
        rejecting.process = execHandle.execHandleRunner.process

        when:
        execHandle.abort()

        then:
        thrown(ProcessExecutionException)
        execHandle.state == ExecHandleState.FAILED
    }

    void "clients can listen to notifications"() {
        ExecHandleListener listener = Mock()
        def execHandle = handle().listener(listener).args(args(TestApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        1 * listener.beforeExecutionStarted(execHandle)
        1 * listener.executionStarted(execHandle)
        1 * listener.executionFinished(execHandle, _ as ExecResult)
        0 * listener._
    }

    void "clients can listen to notifications when execution fails"() {
        ExecHandleListener listener = Mock()
        def execHandle = handle().listener(listener).args(args(BrokenApp.class, "72")).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        1 * listener.beforeExecutionStarted(execHandle)
        1 * listener.executionStarted(execHandle)
        1 * listener.executionFinished(execHandle, _ as ExecResult)
        0 * listener._
    }

    void "propagates listener start notification failure"() {
        def failure = new RuntimeException()
        ExecHandleListener listener = Mock()
        def execHandle = handle().listener(listener).args(args(TestApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        1 * listener.beforeExecutionStarted(execHandle)
        1 * listener.executionStarted(execHandle) >> { throw failure }
        1 * listener.executionFinished(execHandle, _ as ExecResult) >> { ExecHandle h, ExecResult r ->
            assert r.failure.cause == failure
        }
        0 * listener._

        and:
        def e = thrown(ProcessExecutionException)
        e.cause == failure
    }

    void "propagates listener finish notification failure"() {
        def failure = new RuntimeException()
        ExecHandleListener listener = Mock()
        def execHandle = handle().listener(listener).args(args(TestApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        1 * listener.beforeExecutionStarted(execHandle)
        1 * listener.executionStarted(execHandle)
        1 * listener.executionFinished(execHandle, _ as ExecResult) >> { throw failure }
        0 * listener._

        and:
        def e = thrown(ProcessExecutionException)
        e.cause == failure
    }

    void "propagates listener finish notification failure after execution fails"() {
        def failure = new RuntimeException()
        ExecHandleListener listener = Mock()
        def execHandle = handle().listener(listener).args(args(BrokenApp.class, "72")).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        1 * listener.beforeExecutionStarted(execHandle)
        1 * listener.executionStarted(execHandle)
        1 * listener.executionFinished(execHandle, _ as ExecResult) >> { throw failure }
        0 * listener._

        and:
        def e = thrown(ProcessExecutionException)
        e.cause == failure
    }

    void "forks daemon and aborts it"() {
        def output = new ByteArrayOutputStream()
        def execHandle = handle().setDaemon(true).setStandardOutput(output).args(args(SlowDaemonApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        output.toString().contains "I'm the daemon"
        execHandle.state == ExecHandleState.DETACHED

        cleanup:
        execHandle.abort()
    }

    @Ignore //not yet implemented
    void "aborts daemon"() {
        def output = new ByteArrayOutputStream()
        def execHandle = handle().setDaemon(true).setStandardOutput(output).args(args(SlowDaemonApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

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
        def execHandle = handle().setDaemon(true).listener(listener).setStandardOutput(out).args(args(SlowDaemonApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        out.toString().contains "I'm the daemon"
        1 * listener.beforeExecutionStarted(execHandle)
        1 * listener.executionStarted(execHandle)
        0 * listener.executionFinished(_, _)

        cleanup:
        execHandle.abort()
    }

    void "can detach from long daemon and then wait for finish"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().setStandardOutput(out).args(args(SlowDaemonApp.class, "200")).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        out.toString().contains "I'm the daemon"

        when:
        execHandle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
    }

    void "can detach from fast app then wait for finish"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().setStandardOutput(out).args(args(TestApp.class)).build()

        when:
        execHandle.start()
        execHandle.waitForFinish()
        execHandle.waitForFinish()

        then:
        execHandle.state == ExecHandleState.SUCCEEDED
    }

    @Ignore //not yet implemented
    //it may not be easily testable
    void "detach detects when process did not start or died prematurely"() {
        def execHandle = handle().args(args(BrokenApp.class, "72")).build()

        when:
        execHandle.start()
        def detachResult = execHandle.detach()

        then:
        execHandle.state == ExecHandleState.FAILED
        detachResult.processCompleted
        detachResult.executionResult.get().exitValue == 72
    }

    void "can redirect error stream"() {
        def out = new ByteArrayOutputStream()
        def execHandle = handle().args(args(TestApp.class)).setStandardOutput(out).redirectErrorStream().build()

        when:
        execHandle.start()
        execHandle.waitForFinish()

        then:
        ["output args", "error args"].each { out.toString().contains(it) }
    }

    void "exec handle collaborates with streams handler"() {
        given:
        def streamsHandler = Mock(StreamsHandler)
        def execHandle = handle().args(args(TestApp.class)).setDisplayName("foo proc").streamsHandler(streamsHandler).build()

        when:
        execHandle.start()
        def result = execHandle.waitForFinish()

        then:
        result.rethrowFailure()
        1 * streamsHandler.connectStreams(_ as Process, "foo proc", _ as Executor)
        1 * streamsHandler.start()
        1 * streamsHandler.stop()
        0 * streamsHandler._
    }

    @Timeout(2)
    @Ignore //not yet implemented
    void "exec handle can detach with timeout"() {
        given:
        def execHandle = handle().args(args(SlowApp.class)).setTimeout(1).build()

        when:
        execHandle.start()
        def result = execHandle.waitForFinish()

        then:
        result
        //the timeout does not hit
    }

    @Ignore //not yet implemented
    void "exec handle can wait with timeout"() {
        given:
        def execHandle = handle().args(args(SlowApp.class)).setTimeout(1).build()

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

    public static class BrokenApp {
        public static void main(String[] args) {
            System.exit(args[0].toInteger())
        }
    }

    public static class SlowApp {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(10000L)
        }
    }

    public static class ShortApp {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(500L)
        }
    }

    /**
     * Simulates an executor that was stopped while a process was still running: rejects any
     * submission made once {@link #process} has exited. Startup submissions (the runner and the
     * stream pumps) happen while the process is alive and are delegated; only the post-exit
     * completion submission is rejected.
     */
    static class RejectingExecutor implements Executor {
        private final Executor delegate
        volatile Process process

        RejectingExecutor(Executor delegate) {
            this.delegate = delegate
        }

        @Override
        void execute(Runnable command) {
            def p = process
            if (p != null && !p.isAlive()) {
                throw new RejectedExecutionException("executor stopped (test)")
            }
            delegate.execute(command)
        }
    }

    public static class SlowDaemonApp {
        public static void main(String[] args) throws InterruptedException {
            System.out.println("I'm the daemon")
            System.out.close()
            System.err.close()
            int napTime = (args.length == 0) ? 10000L : Integer.parseInt(args[0])
            Thread.sleep(napTime)
        }
    }

    public static class FastDaemonApp {
        public static void main(String[] args) throws InterruptedException {
            System.out.println("I'm the daemon")
            System.out.close()
            System.err.close()
        }
    }

    public static class InputReadingApp {
        public static void main(String[] args) throws InterruptedException {
            ObjectInputStream instr = new ObjectInputStream(System.in)
            Callable<?> main = (Callable<?>) instr.readObject()
            System.out.println(main.call())
        }
    }

    static class AppWithChild {
        static void main(String[] args) throws InterruptedException {
            def java = System.getenv('JAVA_EXE_PATH')
            "$java ${SlowApp.name}".execute().waitFor()
        }
    }

    static class AppWithChildWithGrandChild {
        static void main(String[] args) throws InterruptedException {
            def java = System.getenv('JAVA_EXE_PATH')
            "$java ${AppWithChild.name}".execute().waitFor()
        }
    }
}
