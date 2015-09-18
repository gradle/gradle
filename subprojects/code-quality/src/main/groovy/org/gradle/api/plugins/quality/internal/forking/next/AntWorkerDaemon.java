package org.gradle.api.plugins.quality.internal.forking.next;

import org.gradle.api.plugins.quality.internal.forking.AntResult;
import org.gradle.api.plugins.quality.internal.forking.AntWorkerSpec;


public interface AntWorkerDaemon {
    <T extends AntWorkerSpec> AntResult execute(T spec);
}
