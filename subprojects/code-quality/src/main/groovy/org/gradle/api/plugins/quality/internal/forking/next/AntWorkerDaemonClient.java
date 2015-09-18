package org.gradle.api.plugins.quality.internal.forking.next;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.process.internal.WorkerProcess;


public class AntWorkerDaemonClient implements AntWorkerDaemon, AntWorkerDaemonClientProtocol, Stoppable {
    private final DaemonForkOptions forkOptions;
    private final WorkerProcess workerProcess;
    private final AntWorkerDaemonServerProtocol server;
    private final BlockingQueue<AntResult> compileResults = new SynchronousQueue<AntResult>();

    public AntWorkerDaemonClient(DaemonForkOptions forkOptions, WorkerProcess workerProcess, AntWorkerDaemonServerProtocol server) {

        this.forkOptions = forkOptions;
        this.workerProcess = workerProcess;
        this.server = server;
    }

    @Override
    public <T extends AntWorkerSpec> AntResult execute(T spec) {
        // one problem to solve when allowing multiple threads is how to deal with memory requirements specified by ant tasks
        try {
            server.execute(spec);
            return compileResults.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    @Override
    public void executed(AntResult result) {
        try {
            compileResults.put(result);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void stop() {
        server.stop();
        workerProcess.waitForStop();
    }
}
