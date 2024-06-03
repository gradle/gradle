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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.context.DaemonRequestContext;

import java.io.InputStream;
import java.util.UUID;

/**
 * Takes care of instantiating and wiring together the services required by the daemon client.
 */
public class DaemonClientServices extends DaemonClientServicesSupport {
    public DaemonClientServices(ServiceRegistry parent, DaemonParameters daemonParameters, DaemonRequestContext requestContext, InputStream buildStandardInput) {
        super(parent, daemonParameters, requestContext, buildStandardInput);
    }

    @Provides
    protected DaemonClient createDaemonClient(IdGenerator<UUID> idGenerator) {
        DaemonCompatibilitySpec matchingContextSpec = new DaemonCompatibilitySpec(get(DaemonRequestContext.class));
        return new DaemonClient(
            get(DaemonConnector.class),
            get(OutputEventListener.class),
            matchingContextSpec,
            getBuildStandardInput(),
            get(GlobalUserInputReceiver.class),
            idGenerator,
            get(ProcessEnvironment.class));
    }
}
