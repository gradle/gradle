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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.remote.internal.OutgoingConnector;
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.util.UUID;

/**
 * Services needed by clients that only communicate with running Daemons.
 *
 * Clients created with these services cannot start new daemons.
 *
 * @see DaemonStopClient
 * @see NotifyDaemonAboutChangedPathsClient
 */
@NonNullApi
public class DaemonClientMessageServices implements ServiceRegistrationProvider {

    @Provides
    IdGenerator<UUID> createIdGenerator() {
        return new UUIDGenerator();
    }

    @Provides
    OutgoingConnector createOutgoingConnector() {
        return new TcpOutgoingConnector();
    }

    @Provides
    Clock createClock() {
        return Time.clock();
    }

    @Provides
    BuildOperationIdFactory createBuildOperationIdFactory() {
        return new DefaultBuildOperationIdFactory();
    }

    @Provides
    ProgressLoggerFactory createProgressLoggerFactory(Clock clock, BuildOperationIdFactory buildOperationIdFactory, OutputEventListener outputEventListener) {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(outputEventListener), clock, buildOperationIdFactory);
    }

    @Provides
    DaemonStopClient createDaemonStopClient(DaemonConnector connector, IdGenerator<UUID> idGenerator) {
        return new DaemonStopClient(connector, idGenerator);
    }

    @Provides
    NotifyDaemonAboutChangedPathsClient createNotifyDaemonAboutChangedPathsClient(DaemonConnector connector, IdGenerator<UUID> idGenerator, DaemonRegistry daemonRegistry) {
        return new NotifyDaemonAboutChangedPathsClient(connector, idGenerator, daemonRegistry);
    }

    @Provides
    ReportDaemonStatusClient createReportDaemonStatusClient(DaemonRegistry registry, DaemonConnector connector, IdGenerator<UUID> idGenerator, DocumentationRegistry documentationRegistry) {
        return new ReportDaemonStatusClient(registry, connector, idGenerator, documentationRegistry);
    }

    @Provides
    DaemonConnector createDaemonConnector(DaemonDir daemonDir, DaemonRegistry daemonRegistry, OutgoingConnector outgoingConnector, DaemonStarter daemonStarter, ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, Serializer<BuildAction> buildActionSerializer) {
        return new DefaultDaemonConnector(daemonDir, daemonRegistry, outgoingConnector, daemonStarter, listenerManager.getBroadcaster(DaemonStartListener.class), progressLoggerFactory, DaemonMessageSerializer.create(buildActionSerializer));
    }

    @Provides
    DaemonStarter createDaemonStarter() {
        return new UnavailableDaemonStarter();
    }

    @NonNullApi
    private static class UnavailableDaemonStarter implements DaemonStarter {
        @Override
        public DaemonStartupInfo startDaemon(boolean singleRun) {
            throw new UnsupportedOperationException("Daemons cannot be started with this client.");
        }
    }
}
