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
import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DefaultDaemonContext;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.HandleInvalidateVirtualFileSystem;
import org.gradle.launcher.daemon.server.api.HandleReportStatus;
import org.gradle.launcher.daemon.server.api.HandleStop;
import org.gradle.launcher.daemon.server.exec.CleanUpVirtualFileSystemAfterBuild;
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
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo;
import org.gradle.launcher.daemon.server.scaninfo.DefaultDaemonScanInfo;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;
import org.gradle.launcher.exec.BuildExecutor;
import org.gradle.tooling.internal.provider.action.BuildActionSerializer;

import java.io.File;
import java.util.UUID;

import static org.gradle.internal.FileUtils.canonicalize;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices implements ServiceRegistrationProvider {
    private final DaemonServerConfiguration configuration;
    private final LoggingManagerInternal loggingManager;
    private static final Logger LOGGER = Logging.getLogger(DaemonServices.class);

    public DaemonServices(DaemonServerConfiguration configuration, LoggingManagerInternal loggingManager) {
        this.configuration = configuration;
        this.loggingManager = loggingManager;
    }

    @Provides
    protected DaemonContext createDaemonContext(AgentStatus agentStatus, ProcessEnvironment processEnvironment) {
        LOGGER.debug("Creating daemon context with opts: {}", configuration.getJvmOptions());
        return new DefaultDaemonContext(configuration.getUid(),
            canonicalize(Jvm.current().getJavaHome()),
            JavaLanguageVersion.current(),
            Jvm.current().getVendor(),
            configuration.getBaseDir(),
            processEnvironment.maybeGetPid(),
            configuration.getIdleTimeout(),
            configuration.getJvmOptions(),
            agentStatus.isAgentInstrumentationEnabled(),
            configuration.getNativeServicesMode(),
            configuration.getPriority()
        );
    }

    @Provides
    protected DaemonLogFile createDaemonLogFile(DaemonContext daemonContext, DaemonDir daemonDir) {
        final Long pid = daemonContext.getPid();
        String fileName = "daemon-" + (pid == null ? UUID.randomUUID() : pid) + ".out.log";
        return new DaemonLogFile(new File(daemonDir.getVersionedDir(), fileName));
    }

    @Provides
    protected DaemonHealthCheck createDaemonHealthCheck(ListenerManager listenerManager, HealthExpirationStrategy healthExpirationStrategy) {
        return new DaemonHealthCheck(healthExpirationStrategy, listenerManager);
    }

    @Provides
    protected DaemonRunningStats createDaemonRunningStats() {
        return new DaemonRunningStats();
    }

    @Provides
    protected DaemonScanInfo createDaemonScanInfo(DaemonRunningStats runningStats, ListenerManager listenerManager, DaemonRegistry daemonRegistry) {
        return new DefaultDaemonScanInfo(runningStats, configuration.getIdleTimeout(), configuration.isSingleUse(), daemonRegistry, listenerManager);
    }

    @Provides
    protected MasterExpirationStrategy createMasterExpirationStrategy(Daemon daemon, HealthExpirationStrategy healthExpirationStrategy, ListenerManager listenerManager) {
        return new MasterExpirationStrategy(daemon, configuration, healthExpirationStrategy, listenerManager);
    }

    @Provides
    protected HealthExpirationStrategy createHealthExpirationStrategy(DaemonHealthStats stats, GarbageCollectorMonitoringStrategy strategy) {
        return new HealthExpirationStrategy(stats, strategy);
    }

    @Provides
    protected DaemonHealthStats createDaemonHealthStats(DaemonRunningStats runningStats, GarbageCollectorMonitoringStrategy strategy, ExecutorFactory executorFactory) {
        return new DaemonHealthStats(runningStats, strategy, executorFactory);
    }

    @Provides
    protected GarbageCollectorMonitoringStrategy createGarbageCollectorMonitoringStrategy() {
        return GarbageCollectorMonitoringStrategy.determineGcStrategy();
    }

    @Provides
    protected ImmutableList<DaemonCommandAction> createDaemonCommandActions(
        BuildExecutor buildActionExecuter,
        DaemonContext daemonContext,
        DaemonHealthCheck healthCheck,
        DaemonHealthStats healthStats,
        DaemonRunningStats runningStats,
        ExecutorFactory executorFactory,
        ProcessEnvironment processEnvironment,
        UserInputReader inputReader,
        OutputEventListener eventDispatch,
        DaemonLogFile daemonLogFile,
        GradleUserHomeScopeServiceRegistry userHomeServiceRegistry,
        ListenerManager listenerManager
    ) {
        DaemonDiagnostics daemonDiagnostics = new DaemonDiagnostics(daemonLogFile.getFile(), daemonContext.getPid());
        return ImmutableList.of(
            new HandleStop(listenerManager),
            new HandleInvalidateVirtualFileSystem(userHomeServiceRegistry),
            new HandleCancel(),
            new HandleReportStatus(),
            new CleanUpVirtualFileSystemAfterBuild(executorFactory, userHomeServiceRegistry),
            new ReturnResult(),
            new StartBuildOrRespondWithBusy(daemonDiagnostics), // from this point down, the daemon is 'busy'
            new EstablishBuildEnvironment(processEnvironment),
            new LogToClient(loggingManager, daemonDiagnostics), // from this point down, logging is sent back to the client
            new LogAndCheckHealth(healthStats, healthCheck, runningStats),
            new ForwardClientInput(inputReader, eventDispatch),
            new RequestStopIfSingleUsedDaemon(),
            new ResetDeprecationLogger(),
            new WatchForDisconnection(),
            new ExecuteBuild(buildActionExecuter, runningStats)
        );
    }

    @Provides
    Serializer<BuildAction> createBuildActionSerializer() {
        return BuildActionSerializer.create();
    }

    @Provides
    protected Daemon createDaemon(
        ImmutableList<DaemonCommandAction> actions,
        Serializer<BuildAction> buildActionSerializer,
        ExecutorFactory executorFactory,
        InetAddressFactory inetAddressFactory,
        DaemonRegistry daemonRegistry,
        DaemonContext daemonContext,
        ListenerManager listenerManager
    ) {
        return new Daemon(
            new DaemonTcpServerConnector(
                executorFactory,
                inetAddressFactory,
                DaemonMessageSerializer.create(buildActionSerializer)
            ),
            daemonRegistry,
            daemonContext,
            new DaemonCommandExecuter(configuration, actions),
            executorFactory,
            listenerManager
        );
    }
}
