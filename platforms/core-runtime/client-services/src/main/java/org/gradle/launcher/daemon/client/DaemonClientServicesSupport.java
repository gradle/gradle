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
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.toolchain.DaemonClientToolchainServices;
import org.gradle.launcher.daemon.toolchain.DaemonJavaToolchainQueryService;

import java.io.InputStream;
import java.util.UUID;

/**
 * Some support wiring for daemon clients.
 *
 * @see DaemonClientServices
 */
public abstract class DaemonClientServicesSupport extends DefaultServiceRegistry {
    private final InputStream buildStandardInput;

    public DaemonClientServicesSupport(ServiceRegistry parent, DaemonParameters daemonParameters, DaemonRequestContext requestContext, InputStream buildStandardInput) {
        super(parent);
        this.buildStandardInput = buildStandardInput;
        add(daemonParameters);
        add(requestContext);
        addProvider(new DaemonRegistryServices(daemonParameters.getBaseDir()));
        addProvider(new DaemonClientToolchainServices(daemonParameters.getToolchainConfiguration()));
    }

    protected InputStream getBuildStandardInput() {
        return buildStandardInput;
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
    DaemonConnector createDaemonConnector(DaemonRegistry daemonRegistry, OutgoingConnector outgoingConnector, DaemonStarter daemonStarter, ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, Serializer<BuildAction> buildActionSerializer) {
        return new DefaultDaemonConnector(daemonRegistry, outgoingConnector, daemonStarter, listenerManager.getBroadcaster(DaemonStartListener.class), progressLoggerFactory, DaemonMessageSerializer.create(buildActionSerializer));
    }

    @Provides
    DaemonStarter createDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters, DaemonGreeter daemonGreeter, JvmVersionValidator jvmVersionValidator, DaemonJavaToolchainQueryService daemonJavaToolchainQueryService) {
        return new DefaultDaemonStarter(daemonDir, daemonParameters, daemonGreeter, jvmVersionValidator, daemonJavaToolchainQueryService);
    }
}
