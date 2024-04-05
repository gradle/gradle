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
import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.remote.internal.OutgoingConnector;
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.jvm.DaemonJavaToolchainQueryService;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Some support wiring for daemon clients.
 *
 * @see DaemonClientServices
 */
public abstract class DaemonClientServicesSupport extends DefaultServiceRegistry {
    private final InputStream buildStandardInput;

    public DaemonClientServicesSupport(ServiceRegistry parent, InputStream buildStandardInput) {
        super(parent);
        this.buildStandardInput = buildStandardInput;
    }

    protected InputStream getBuildStandardInput() {
        return buildStandardInput;
    }

    DaemonStopClient createDaemonStopClient(DaemonConnector connector, IdGenerator<UUID> idGenerator) {
        return new DaemonStopClient(connector, idGenerator);
    }

    NotifyDaemonAboutChangedPathsClient createNotifyDaemonAboutChangedPathsClient(DaemonConnector connector, IdGenerator<UUID> idGenerator, DaemonRegistry daemonRegistry) {
        return new NotifyDaemonAboutChangedPathsClient(connector, idGenerator, daemonRegistry);
    }

    ReportDaemonStatusClient createReportDaemonStatusClient(DaemonRegistry registry, DaemonConnector connector, IdGenerator<UUID> idGenerator, DocumentationRegistry documentationRegistry) {
        return new ReportDaemonStatusClient(registry, connector, idGenerator, documentationRegistry);
    }

    protected DaemonClient createDaemonClient(IdGenerator<UUID> idGenerator) {
        DaemonCompatibilitySpec matchingContextSpec = new DaemonCompatibilitySpec(get(DaemonRequestContext.class));
        return new DaemonClient(
                get(DaemonConnector.class),
                get(OutputEventListener.class),
                matchingContextSpec,
                buildStandardInput,
                get(GlobalUserInputReceiver.class),
                get(ExecutorFactory.class),
                idGenerator,
                get(ProcessEnvironment.class));
    }

    IdGenerator<UUID> createIdGenerator() {
        return new UUIDGenerator();
    }

    OutgoingConnector createOutgoingConnector() {
        return new TcpOutgoingConnector();
    }

    Clock createClock() {
        return Time.clock();
    }

    BuildOperationIdFactory createBuildOperationIdFactory() {
        return new DefaultBuildOperationIdFactory();
    }

    ProgressLoggerFactory createProgressLoggerFactory(Clock clock, BuildOperationIdFactory buildOperationIdFactory, OutputEventListener outputEventListener) {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(outputEventListener), clock, buildOperationIdFactory);
    }

    DaemonConnector createDaemonConnector(DaemonRegistry daemonRegistry, OutgoingConnector outgoingConnector, DaemonStarter daemonStarter, ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, Serializer<BuildAction> buildActionSerializer) {
        return new DefaultDaemonConnector(daemonRegistry, outgoingConnector, daemonStarter, listenerManager.getBroadcaster(DaemonStartListener.class), progressLoggerFactory, DaemonMessageSerializer.create(buildActionSerializer));
    }
    protected DaemonJavaToolchainQueryService createDaemonJavaToolchainQueryService(JavaInstallationRegistry javaInstallationRegistry) {
        return new DaemonJavaToolchainQueryService(javaInstallationRegistry);
    }
    protected JavaInstallationRegistry createJavaInstallationRegistry(List<InstallationSupplier> installationSuppliers, JvmMetadataDetector jvmMetadataDetector, ProgressLoggerFactory progressLoggerFactory) {
        return new JavaInstallationRegistry(installationSuppliers, jvmMetadataDetector, null, OperatingSystem.current(), progressLoggerFactory, null);
    }
    protected InstallationSupplier createAutoInstalledInstallationSupplier(JdkCacheDirectory jdkCacheDirectory) {
        return new AutoInstalledInstallationSupplier(jdkCacheDirectory);
    }
    protected InstallationSupplier createCurrentInstallationSupplier() {
        return new CurrentInstallationSupplier();
    }

    // TODO: Decide if we should use all of the different installation suppliers for Gradle daemons or not
    protected JdkCacheDirectory createJdkCacheDirectory(GradleUserHomeDirProvider gradleUserHomeDirProvider, FileLockManager fileLockManager, JvmMetadataDetector jvmMetadataDetector) {
        return new JdkCacheDirectory(gradleUserHomeDirProvider, null, fileLockManager, jvmMetadataDetector);
    }
}
