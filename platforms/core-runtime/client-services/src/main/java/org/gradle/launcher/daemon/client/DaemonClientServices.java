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

import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.service.Provides;
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.context.DaemonRequestContext;

import java.io.InputStream;
import java.util.UUID;

/**
 * Takes care of instantiating and wiring together the services required by the daemon client.
 */
public class DaemonClientServices extends DaemonClientServicesSupport {

    public DaemonClientServices(InputStream buildStandardInput) {
        super(buildStandardInput);
    }

    @Provides
    protected DaemonClient createDaemonClient(
        DaemonRequestContext daemonRequestContext,
        DaemonConnector daemonConnector,
        OutputEventListener outputEventListener,
        GlobalUserInputReceiver globalUserInputReceiver,
        IdGenerator<UUID> idGenerator,
        ProcessEnvironment processEnvironment
    ) {
        DaemonCompatibilitySpec matchingContextSpec = new DaemonCompatibilitySpec(daemonRequestContext);
        return new DaemonClient(daemonConnector, outputEventListener, matchingContextSpec, getBuildStandardInput(), globalUserInputReceiver, idGenerator, processEnvironment);
    }
}
