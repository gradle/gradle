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
package org.gradle.launcher.daemon.server;

import com.google.common.collect.ImmutableList;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.HandleInvalidateVirtualFileSystem;
import org.gradle.launcher.daemon.server.api.HandleReportStatus;
import org.gradle.launcher.daemon.server.api.HandleStop;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment;
import org.gradle.launcher.daemon.server.exec.ExecuteBuild;
import org.gradle.launcher.daemon.server.exec.ForwardClientInput;
import org.gradle.launcher.daemon.server.exec.HandleCancel;
import org.gradle.launcher.daemon.server.exec.LogAndCheckHealth;
import org.gradle.launcher.daemon.server.exec.LogToClient;
import org.gradle.launcher.daemon.server.exec.RequestStopIfSingleUsedDaemon;
import org.gradle.launcher.daemon.server.exec.ResetDeprecationLogger;
import org.gradle.launcher.daemon.server.exec.ReturnResult;
import org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy;
import org.gradle.launcher.daemon.server.exec.WatchForDisconnection;
import org.gradle.launcher.daemon.server.health.DaemonHealthCheck;
import org.gradle.launcher.daemon.server.health.DaemonHealthStats;
import org.gradle.launcher.daemon.server.health.DaemonMemoryStatus;
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo;
import org.gradle.launcher.daemon.server.scaninfo.DefaultDaemonScanInfo;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.tooling.internal.provider.action.BuildActionSerializer;

import java.io.File;
import java.util.UUID;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final DaemonServerConfiguration configuration;
    private final LoggingManagerInternal loggingManager;
    private static final Logger LOGGER = Logging.getLogger(DaemonServices.class);

    public DaemonServices(DaemonServerConfiguration configuration, ServiceRegistry loggingServices, LoggingManagerInternal loggingManager, ClassPath additionalModuleClassPath) {
        super(NativeServices.getInstance(), loggingServices);
        this.configuration = configuration;
        this.loggingManager = loggingManager;

        addProvider(new DaemonRegistryServices(configuration.getBaseDir()));
        addProvider(new GlobalScopeServices(!configuration.isSingleUse(), additionalModuleClassPath));
    }

    protected DaemonContext createDaemonContext() {
        DaemonContextBuilder builder = new DaemonContextBuilder(get(ProcessEnvironment.class));
        builder.setDaemonRegistryDir(configuration.getBaseDir());
        builder.setIdleTimeout(configuration.getIdleTimeout());
        builder.setUid(configuration.getUid());
        builder.setPriority(configuration.getPriority());

        LOGGER.debug("Creating daemon context with opts: {}", configuration.getJvmOptions());

        builder.setDaemonOpts(configuration.getJvmOptions());

        return builder.create();
    }

    public File getDaemonLogFile() {
        final DaemonContext daemonContext = get(DaemonContext.class);
        final Long pid = daemonContext.getPid();
        String fileName = "daemon-" + (pid == null ? UUID.randomUUID() : pid) + ".out.log";
        return new File(get(DaemonDir.class).getVersionedDir(), fileName);
    }

    protected DaemonMemoryStatus createDaemonMemoryStatus(DaemonHealthStats healthStats, GarbageCollectorMonitoringStrategy strategy) {
        return new DaemonMemoryStatus(healthStats, strategy.getHeapUsageThreshold(), strategy.getGcRateThreshold(), strategy.getNonHeapUsageThreshold(), strategy.getThrashingThreshold());
    }

    protected DaemonHealthCheck createDaemonHealthCheck(ListenerManager listenerManager, HealthExpirationStrategy healthExpirationStrategy) {
        return new DaemonHealthCheck(healthExpirationStrategy, listenerManager);
    }

    protected DaemonRunningStats createDaemonRunningStats() {
        return new DaemonRunningStats();
    }

    protected DaemonScanInfo createDaemonScanInfo(DaemonRunningStats runningStats, ListenerManager listenerManager) {
        return new DefaultDaemonScanInfo(runningStats, configuration.getIdleTimeout(), configuration.isSingleUse(), get(DaemonRegistry.class), listenerManager);
    }

    protected MasterExpirationStrategy createMasterExpirationStrategy(Daemon daemon, HealthExpirationStrategy healthExpirationStrategy, ListenerManager listenerManager) {
        return new MasterExpirationStrategy(daemon, configuration, healthExpirationStrategy, listenerManager);
    }

    protected HealthExpirationStrategy createHealthExpirationStrategy(DaemonMemoryStatus memoryStatus) {
        return new HealthExpirationStrategy(memoryStatus);
    }

    protected DaemonHealthStats createDaemonHealthStats(DaemonRunningStats runningStats, GarbageCollectorMonitoringStrategy strategy, ExecutorFactory executorFactory) {
        return new DaemonHealthStats(runningStats, strategy, executorFactory);
    }

    protected GarbageCollectorMonitoringStrategy createGarbageCollectorMonitoringStrategy() {
        return GarbageCollectorMonitoringStrategy.determineGcStrategy();
    }

    protected ImmutableList<DaemonCommandAction> createDaemonCommandActions(DaemonContext daemonContext, ProcessEnvironment processEnvironment, DaemonHealthStats healthStats, DaemonHealthCheck healthCheck, BuildExecuter buildActionExecuter, DaemonRunningStats runningStats) {
        File daemonLog = getDaemonLogFile();
        DaemonDiagnostics daemonDiagnostics = new DaemonDiagnostics(daemonLog, daemonContext.getPid());
        return ImmutableList.of(
            new HandleStop(get(ListenerManager.class)),
            new HandleInvalidateVirtualFileSystem(get(GradleUserHomeScopeServiceRegistry.class)),
            new HandleCancel(),
            new HandleReportStatus(),
            new ReturnResult(),
            new StartBuildOrRespondWithBusy(daemonDiagnostics), // from this point down, the daemon is 'busy'
            new EstablishBuildEnvironment(processEnvironment),
            new LogToClient(loggingManager, daemonDiagnostics), // from this point down, logging is sent back to the client
            new LogAndCheckHealth(healthStats, healthCheck),
            new ForwardClientInput(),
            new RequestStopIfSingleUsedDaemon(),
            new ResetDeprecationLogger(),
            new WatchForDisconnection(),
            new ExecuteBuild(buildActionExecuter, runningStats)
        );

    }

    Serializer<BuildAction> createBuildActionSerializer() {
        return BuildActionSerializer.create();
    }

    protected Daemon createDaemon(ImmutableList<DaemonCommandAction> actions, Serializer<BuildAction> buildActionSerializer) {
        return new Daemon(
            new DaemonTcpServerConnector(
                get(ExecutorFactory.class),
                get(InetAddressFactory.class),
                DaemonMessageSerializer.create(buildActionSerializer)
            ),
            get(DaemonRegistry.class),
            get(DaemonContext.class),
            new DaemonCommandExecuter(configuration, actions),
            get(ExecutorFactory.class),
            get(ListenerManager.class)
        );
    }
}
