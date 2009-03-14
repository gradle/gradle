package org.gradle.util.exec;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleRunner implements Runnable {

    private final ProcessBuilderFactory processBuilderFactory;
    private final DefaultExecHandle execHandle;
    private final AtomicBoolean keepWaiting;
    private final ExecutorService threadPool;

    public ExecHandleRunner(DefaultExecHandle execHandle, ExecutorService threadPool) {
        if ( execHandle == null ) throw new IllegalArgumentException("execHandle == null!");
        this.processBuilderFactory = new ProcessBuilderFactory();
        this.execHandle = execHandle;
        this.keepWaiting = new AtomicBoolean(true);
        this.threadPool = threadPool;
    }

    public void stopWaiting() {
        keepWaiting.set(false);
    }

    public void run() {
        final ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
        final long keepWaitingTimeout = execHandle.getKeepWaitingTimeout();

        try {
            final Process process = processBuilder.start();

            final ExecOutputHandleRunner standardOutputHandleRunner = new ExecOutputHandleRunner(process.getInputStream(), execHandle.getStandardOutputHandle());
            final ExecOutputHandleRunner errorOutputHandleRunner = new ExecOutputHandleRunner(process.getErrorStream(), execHandle.getErrorOutputHandle());

            threadPool.execute(standardOutputHandleRunner);
            threadPool.execute(errorOutputHandleRunner);

            // signal started after all threads are started otherwise RejectedExecutionException may be thrown
            // by the ExecutorService because shutdown may already be called on it
            // especially when the startAndWaitForFinish method is used on the ExecHandle.
            execHandle.started();

            boolean processFinishedNormally = false;
            while ( keepWaiting.get() && !processFinishedNormally ) {
                try {
                    process.waitFor();
                    processFinishedNormally = true;
                }
                catch (InterruptedException e) {
                    // ignore
                }
                try {
                    Thread.sleep(keepWaitingTimeout);
                }
                catch (InterruptedException e) {
                    // ignore
                }
            }

            if ( !keepWaiting.get() ) {
                process.destroy();
                execHandle.aborted();
            }
            else {
                final int exitCode = process.exitValue();

                execHandle.finished(exitCode);
            }
        }
        catch ( Throwable t ) {
            execHandle.failed(t);
        }

    }
}
