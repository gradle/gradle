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
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.io.InputStream;
import java.util.UUID;

/**
 * Some support wiring for daemon clients.
 *
 * @see DaemonClientServices
 */
public abstract class DaemonClientServicesSupport implements ServiceRegistrationProvider {

    private final InputStream buildStandardInput;

    public DaemonClientServicesSupport(InputStream buildStandardInput) {
        this.buildStandardInput = buildStandardInput;
    }

    protected InputStream getBuildStandardInput() {
        return buildStandardInput;
    }

    public void configure(ServiceRegistration registration) {
        registration.add(OutgoingConnector.class, TcpOutgoingConnector.class);
        registration.add(BuildOperationIdFactory.class, DefaultBuildOperationIdFactory.class);
        registration.add(DaemonStarter.class, DefaultDaemonStarter.class);
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
    IdGenerator<UUID> createIdGenerator() {
        return new UUIDGenerator();
    }

    @Provides
    Clock createClock() {
        return Time.clock();
    }

    @Provides
    ProgressLoggerFactory createProgressLoggerFactory(Clock clock, BuildOperationIdFactory buildOperationIdFactory, OutputEventListener outputEventListener) {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(outputEventListener), clock, buildOperationIdFactory);
    }

    @Provides
    DaemonConnector createDaemonConnector(DaemonRegistry daemonRegistry, OutgoingConnector outgoingConnector, DaemonStarter daemonStarter, ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, Serializer<BuildAction> buildActionSerializer) {
        return new DefaultDaemonConnector(daemonRegistry, outgoingConnector, daemonStarter, listenerManager.getBroadcaster(DaemonStartListener.class), progressLoggerFactory, DaemonMessageSerializer.create(buildActionSerializer));
    }
}
