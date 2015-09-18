package org.gradle.api.plugins.quality.internal.forking.next;

import org.gradle.api.plugins.quality.internal.forking.AntResult;


public interface AntWorkerDaemonClientProtocol {
    void executed(AntResult result);
}
