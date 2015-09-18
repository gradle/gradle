package org.gradle.api.plugins.quality.internal.forking.next;

import java.io.File;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.internal.concurrent.Stoppable;


@ThreadSafe
public class AntWorkerDaemonManager implements AntWorkerDaemonFactory, Stoppable {

    private static final Logger LOGGER = Logging.getLogger(AntWorkerDaemonManager.class);

    AntWorkerClientsManager clientsManager;

    public AntWorkerDaemonManager(AntWorkerClientsManager clientsManager) {
        this.clientsManager = clientsManager;
    }

    @Override
    public AntWorkerDaemon getDaemon(final File workingDir, final DaemonForkOptions forkOptions) {
        return new AntWorkerDaemon() {
            public <T extends AntWorkerSpec> AntResult execute(T spec) {
                AntWorkerDaemonClient client = clientsManager.reserveIdleClient(forkOptions);
                if (client == null) {
                    LOGGER.lifecycle("====================> Getting new client");
                    client = clientsManager.reserveNewClient(workingDir, forkOptions);
                }
                try {
                    return client.execute(spec);
                } finally {
                    LOGGER.lifecycle("Releasing client: {}", client);
                    clientsManager.release(client);
                }
            }
        };
    }

    public void stop() {
        clientsManager.stop();
    }
}
