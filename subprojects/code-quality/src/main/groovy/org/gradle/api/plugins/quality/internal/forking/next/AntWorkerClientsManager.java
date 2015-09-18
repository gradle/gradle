package org.gradle.api.plugins.quality.internal.forking.next;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.CompositeStoppable;


public class AntWorkerClientsManager {

    private static final Logger LOGGER = Logging.getLogger(AntWorkerClientsManager.class);

    private final Object lock = new Object();
    private final List<AntWorkerDaemonClient> allClients = new ArrayList<AntWorkerDaemonClient>();
    private final List<AntWorkerDaemonClient> idleClients = new ArrayList<AntWorkerDaemonClient>();

    private AntWorkerDaemonStarter antWorkerDaemonStarter;

    public AntWorkerClientsManager(AntWorkerDaemonStarter antWorkerDaemonStarter) {
        this.antWorkerDaemonStarter = antWorkerDaemonStarter;
    }

    public AntWorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return reserveIdleClient(forkOptions, idleClients);
    }

    AntWorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions, List<AntWorkerDaemonClient> clients) {
        synchronized (lock) {
            Iterator<AntWorkerDaemonClient> it = clients.iterator();
            while (it.hasNext()) {
                AntWorkerDaemonClient candidate = it.next();
                if (candidate.isCompatibleWith(forkOptions)) {
                    it.remove();
                    return candidate;
                }
            }
            return null;
        }
    }

    public AntWorkerDaemonClient reserveNewClient(File workingDir, DaemonForkOptions forkOptions) {
        //allow the daemon to be started concurrently
        AntWorkerDaemonClient client = antWorkerDaemonStarter.startDaemon(workingDir, forkOptions);
        synchronized (lock) {
            allClients.add(client);
        }
        return client;
    }

    public void release(AntWorkerDaemonClient client) {
        synchronized (lock) {
            idleClients.add(client);
        }
    }

    public void stop() {
        synchronized (lock) {
            LOGGER.debug("Stopping {} ant worker daemon(s).", allClients.size());
            CompositeStoppable.stoppable(allClients).stop();
            LOGGER.info("Stopped {} ant worker daemon(s).", allClients.size());
            allClients.clear();
        }
    }
}
