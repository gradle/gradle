/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.function.Consumer;

/**
 * Manages the lifecycle for creating {@link DaemonStopClient}s and using them.
 */
@NonNullApi
@ServiceScope(Scope.Global.class)
public class DaemonStopClientExecuter {

    private final DaemonClientFactory daemonClientFactory;

    public DaemonStopClientExecuter(DaemonClientFactory daemonClientFactory) {
        this.daemonClientFactory = daemonClientFactory;
    }

    public void execute(ServiceRegistry loggingServices, File daemonBaseDir, Consumer<DaemonStopClient> action) {
        ServiceRegistry clientServices = daemonClientFactory.createMessageDaemonServices(loggingServices, daemonBaseDir);
        try {
            DaemonStopClient daemonStopClient = clientServices.get(DaemonStopClient.class);
            action.accept(daemonStopClient);
        } finally {
            CompositeStoppable.stoppable(clientServices).stop();
        }
    }
}
