/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.client;

import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.messaging.remote.internal.OutgoingConnector;

/**
 * Wires together the embedded daemon.
 */
public class EmbeddedDaemonClientServices extends DaemonClientServicesSupport {

    private final boolean displayOutput;

    public EmbeddedDaemonClientServices() {
        this(LoggingServiceRegistry.newCommandLineProcessLogging(), false);
    }

    public EmbeddedDaemonClientServices(ServiceRegistry loggingServices, boolean displayOutput) {
        super(loggingServices);
        this.displayOutput = displayOutput;
    }

    protected DaemonConnector createDaemonConnector() {
        return new EmbeddedDaemonConnector((EmbeddedDaemonRegistry)get(DaemonRegistry.class), makeDaemonCompatibilitySpec(), get(OutgoingConnector.class), getLoggingServices());
    }

    protected DaemonRegistry createDaemonRegistry() {
        return new EmbeddedDaemonRegistry();
    }

    protected OutputEventListener createOutputEventListener() {
        if (displayOutput) {
            return super.createOutputEventListener();
        } else {
            return new OutputEventListener() { public void onOutput(OutputEvent event) {} };
        }
    }
}