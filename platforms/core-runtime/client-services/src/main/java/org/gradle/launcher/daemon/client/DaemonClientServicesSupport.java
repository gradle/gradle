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

import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory;
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.layout.GlobalCacheDir;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.UUIDGenerator;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmVersionDetector;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.jvm.inspection.PersistentJvmMetadataDetector;
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
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.toolchain.DaemonJavaToolchainQueryService;
import org.gradle.process.internal.ExecHandleFactory;

import java.io.InputStream;
import java.util.UUID;

/**
 * Some support wiring for daemon clients.
 *
 * @see DaemonClientServices
 */
public abstract class DaemonClientServicesSupport implements ServiceRegistrationProvider {
    private final InputStream buildStandardInput;

    public void configure(ServiceRegistration registration) {
        registration.add(GlobalCacheDir.class);
    }

    public DaemonClientServicesSupport(InputStream buildStandardInput) {
        this.buildStandardInput = buildStandardInput;
    }

    protected InputStream getBuildStandardInput() {
        return buildStandardInput;
    }

    @Provides
    CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        return new DefaultCacheFactory(fileLockManager, executorFactory.create("Cache workers"));
    }

    @Provides
    UnscopedCacheBuilderFactory createCacheRepository(CacheFactory cacheFactory) {
        return new DefaultUnscopedCacheBuilderFactory(cacheFactory);
    }

    @Provides
    DefaultGlobalScopedCacheBuilderFactory createGlobalScopedCache(GlobalCacheDir globalCacheDir, UnscopedCacheBuilderFactory unscopedCacheBuilderFactory) {
        return new DefaultGlobalScopedCacheBuilderFactory(globalCacheDir.getDir(), unscopedCacheBuilderFactory);
    }

    @Provides
    JvmVersionValidator createJvmVersionValidator() {
        return new JvmVersionValidator();
    }

    @Provides
    JvmMetadataDetector createJvmMetadataDetector(ExecHandleFactory execHandleFactory, TemporaryFileProvider temporaryFileProvider, GlobalScopedCacheBuilderFactory globalScopedCacheBuilderFactory) {
        return new PersistentJvmMetadataDetector(new DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider), globalScopedCacheBuilderFactory.createCacheBuilder("jvms"));
    }

    @Provides
    JvmVersionDetector createJvmVersionDetector(JvmMetadataDetector detector) {
        return new DefaultJvmVersionDetector(detector);
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
    DaemonConnector createDaemonConnector(DaemonDir daemonDir, DaemonRegistry daemonRegistry, OutgoingConnector outgoingConnector, DaemonStarter daemonStarter, ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, Serializer<BuildAction> buildActionSerializer) {
        return new DefaultDaemonConnector(daemonDir, daemonRegistry, outgoingConnector, daemonStarter, listenerManager.getBroadcaster(DaemonStartListener.class), progressLoggerFactory, DaemonMessageSerializer.create(buildActionSerializer));
    }

    @Provides
    DaemonStarter createDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters, DaemonGreeter daemonGreeter, JvmVersionValidator jvmVersionValidator, JvmVersionDetector jvmVersionDetector, DaemonJavaToolchainQueryService daemonJavaToolchainQueryService, DaemonRequestContext daemonRequestContext) {
        return new DefaultDaemonStarter(daemonDir, daemonParameters, daemonRequestContext, daemonGreeter, jvmVersionValidator, jvmVersionDetector, daemonJavaToolchainQueryService);
    }
}
