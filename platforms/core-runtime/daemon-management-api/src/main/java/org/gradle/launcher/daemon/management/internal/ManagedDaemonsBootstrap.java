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
package org.gradle.launcher.daemon.management.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.logging.LoggingManagerFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.BasicGlobalScopeServices;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.client.DefaultDaemonConnector;
import org.gradle.launcher.daemon.logging.DaemonLogConstants;
import org.gradle.launcher.daemon.management.ManagedDaemons;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;

import java.io.File;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Wires up a {@link ManagedDaemons} for the current Gradle version. This owns the management-only client: it
 * reuses the shared connector, registry access and protocol dispatchers, but does not bring along the full
 * build client. A daemon started for a build is never involved, so the connector is given a no-op starter and
 * start listener, and an unusable build-action serializer (the management commands never carry a build).
 */
public final class ManagedDaemonsBootstrap {

    private ManagedDaemonsBootstrap() {
    }

    /**
     * Resolves the default daemon registry directory for a Gradle user home, honouring the
     * {@code org.gradle.daemon.registry.base} override and otherwise falling back to {@code <GUH>/daemon}.
     */
    public static File defaultRegistryDir(File gradleUserHomeDir) {
        // Keep in sync with DaemonBuildOptions.BaseDirOption.GRADLE_PROPERTY. That constant lives in the
        // launcher module, which this layer must not depend on, so the property key is inlined here.
        String registryBase = System.getProperty("org.gradle.daemon.registry.base");
        if (registryBase != null) {
            return new File(registryBase);
        }
        return new File(gradleUserHomeDir, DaemonLogConstants.DAEMON_LOG_DIR);
    }

    /**
     * Builds a self-contained client, runs {@code action}, then tears down services and logging.
     */
    public static void withManagedDaemons(File gradleUserHomeDir, File daemonRegistryDir, Consumer<? super ManagedDaemons> action) {
        NativeServices.initializeOnClient(gradleUserHomeDir, NativeServices.NativeServicesMode.fromSystemProperties());
        ServiceRegistry loggingServices = LoggingServiceRegistry.newCommandLineProcessLogging();
        LoggingManagerInternal loggingManager = loggingServices.get(LoggingManagerFactory.class).createLoggingManager();
        loggingManager.setLevelInternal(LogLevel.LIFECYCLE);
        loggingManager.start();
        try {
            ServiceRegistry services = ServiceRegistryBuilder.builder()
                .displayName("daemon management services")
                .parent(loggingServices)
                .parent(NativeServices.getInstance())
                .provider(new BasicGlobalScopeServices())
                .provider(new DaemonRegistryServices(daemonRegistryDir))
                .build();
            try {
                action.accept(newManagedDaemons(services));
            } finally {
                CompositeStoppable.stoppable(services).stop();
            }
        } finally {
            loggingManager.stop();
        }
    }

    /**
     * Adapts an existing daemon message-services registry into a {@link ManagedDaemons}, reusing its connector
     * and registry access. Used by callers that already live inside the Gradle service graph.
     */
    public static ManagedDaemons fromMessageServices(ServiceRegistry messageServices) {
        return new DefaultManagedDaemons(
            messageServices.get(DaemonRegistry.class),
            messageServices.get(DaemonConnector.class),
            idGenerator(messageServices),
            messageServices.get(DocumentationRegistry.class));
    }

    private static ManagedDaemons newManagedDaemons(ServiceRegistry services) {
        DaemonRegistry registry = services.get(DaemonRegistry.class);
        DaemonDir daemonDir = services.get(DaemonDir.class);
        OutputEventListener outputEventListener = services.get(OutputEventListener.class);
        ProgressLoggerFactory progressLoggerFactory = new DefaultProgressLoggerFactory(
            new ProgressLoggingBridge(outputEventListener), Time.clock(), new DefaultBuildOperationIdFactory());
        DaemonConnector connector = new DefaultDaemonConnector(
            daemonDir,
            registry,
            new TcpOutgoingConnector(),
            new UnavailableDaemonStarter(),
            new NoOpDaemonStartListener(),
            progressLoggerFactory,
            DaemonMessageSerializer.create(new UnusableBuildActionSerializer()));
        return new DefaultManagedDaemons(registry, connector, new UUIDGenerator(), new DocumentationRegistry());
    }

    @SuppressWarnings("unchecked")
    private static IdGenerator<UUID> idGenerator(ServiceRegistry services) {
        return services.get(IdGenerator.class);
    }
}
