package org.gradle.api.plugins.quality.internal.forking.next;

import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;
import org.gradle.internal.concurrent.Stoppable;


public interface AntWorkerDaemonServerProtocol extends Stoppable {
    <T extends AntWorkerSpec> void executeSpec(T spec);
}
