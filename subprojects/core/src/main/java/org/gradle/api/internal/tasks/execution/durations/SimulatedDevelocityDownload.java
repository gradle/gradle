/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution.durations;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.buildtree.BuildTreeLifecycleListener;
import org.gradle.internal.service.scopes.ListenerService;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Simulates downloading historical timing data from Develocity when the build starts, when enabled with {@code --simulate-dv-on}.
 * Runs whether or not the configuration cache is reused.
 */
@ListenerService
@ServiceScope(Scope.BuildTree.class)
public class SimulatedDevelocityDownload implements BuildTreeLifecycleListener {
    private static final Logger LOGGER = Logging.getLogger(SimulatedDevelocityDownload.class);
    private static final long DOWNLOAD_MILLIS = 5000;

    private final StartParameterInternal startParameter;

    public SimulatedDevelocityDownload(StartParameterInternal startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public void afterStart() {
        if (!startParameter.isSimulateDevelocityOn()) {
            return;
        }
        LOGGER.lifecycle("Downloading historical timing data using the power of DEVELOCITY...");
        try {
            Thread.sleep(DOWNLOAD_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
