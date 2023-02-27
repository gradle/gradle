/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.cli;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.agents.AgentInitializer;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.BasicGlobalScopeServices;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.client.ReportDaemonStatusClient;
import org.gradle.launcher.daemon.configuration.BuildProcess;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.ForegroundDaemonConfiguration;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.launcher.exec.DefaultBuildActionParameters;

import java.lang.management.ManagementFactory;
import java.util.UUID;

class BuildActionsFactory implements CommandLineActionCreator {
    private final ParametersConverter parametersConverter;
    private final ServiceRegistry loggingServices;
    private final JvmVersionDetector jvmVersionDetector;
    private final FileCollectionFactory fileCollectionFactory;
    private final ServiceRegistry basicServices;

    public BuildActionsFactory(ServiceRegistry loggingServices) {
        basicServices = ServiceRegistryBuilder.builder()
            .parent(loggingServices)
            .parent(NativeServices.getInstance())
            .provider(new BasicGlobalScopeServices()).build();
        this.loggingServices = loggingServices;
        fileCollectionFactory = basicServices.get(FileCollectionFactory.class);
        parametersConverter = new ParametersConverter(new BuildLayoutFactory(), basicServices.get(FileCollectionFactory.class));
        jvmVersionDetector = basicServices.get(JvmVersionDetector.class);
    }

    @Override
    public void configureCommandLineParser(CommandLineParser parser) {
        parametersConverter.configure(parser);
    }

    @Override
    public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
        Parameters parameters = parametersConverter.convert(commandLine, null);

        parameters.getDaemonParameters().applyDefaultsFor(jvmVersionDetector.getJavaVersion(parameters.getDaemonParameters().getEffectiveJvm()));

        if (parameters.getDaemonParameters().isStop()) {
            return Actions.toAction(stopAllDaemons(parameters.getDaemonParameters()));
        }
        if (parameters.getDaemonParameters().isStatus()) {
            return Actions.toAction(showDaemonStatus(parameters.getDaemonParameters()));
        }
        if (parameters.getDaemonParameters().isForeground()) {
            DaemonParameters daemonParameters = parameters.getDaemonParameters();
            ForegroundDaemonConfiguration conf = new ForegroundDaemonConfiguration(
                UUID.randomUUID().toString(), daemonParameters.getBaseDir(), daemonParameters.getIdleTimeout(), daemonParameters.getPeriodicCheckInterval(), fileCollectionFactory,
                daemonParameters.shouldApplyInstrumentationAgent());
            return Actions.toAction(new ForegroundDaemonAction(loggingServices, conf));
        }
        if (parameters.getDaemonParameters().isEnabled()) {
            return Actions.toAction(runBuildWithDaemon(parameters.getStartParameter(), parameters.getDaemonParameters()));
        }
        if (canUseCurrentProcess(parameters.getDaemonParameters())) {
            return Actions.toAction(runBuildInProcess(parameters.getStartParameter(), parameters.getDaemonParameters()));
        }

        return Actions.toAction(runBuildInSingleUseDaemon(parameters.getStartParameter(), parameters.getDaemonParameters()));
    }

    private Runnable stopAllDaemons(DaemonParameters daemonParameters) {
        ServiceRegistry clientSharedServices = createGlobalClientServices(false);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createMessageDaemonServices(loggingServices.get(OutputEventListener.class), daemonParameters);
        DaemonStopClient stopClient = clientServices.get(DaemonStopClient.class);
        return new StopDaemonAction(stopClient);
    }

    private Runnable showDaemonStatus(DaemonParameters daemonParameters) {
        ServiceRegistry clientSharedServices = createGlobalClientServices(false);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createMessageDaemonServices(loggingServices.get(OutputEventListener.class), daemonParameters);
        ReportDaemonStatusClient statusClient = clientServices.get(ReportDaemonStatusClient.class);
        return new ReportDaemonStatusAction(statusClient);
    }

    private Runnable runBuildWithDaemon(StartParameterInternal startParameter, DaemonParameters daemonParameters) {
        // Create a client that will match based on the daemon startup parameters.
        ServiceRegistry clientSharedServices = createGlobalClientServices(true);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createBuildClientServices(loggingServices.get(OutputEventListener.class), daemonParameters, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return runBuildAndCloseServices(startParameter, daemonParameters, client, clientSharedServices, clientServices);
    }

    @VisibleForTesting
    boolean canUseCurrentProcess(DaemonParameters requiredBuildParameters) {
        BuildProcess currentProcess = new BuildProcess(fileCollectionFactory);
        return currentProcess.configureForBuild(requiredBuildParameters);
    }

    private Runnable runBuildInProcess(StartParameterInternal startParameter, DaemonParameters daemonParameters) {
        ServiceRegistry globalServices = ServiceRegistryBuilder.builder()
            .displayName("Global services")
            .parent(loggingServices)
            .parent(NativeServices.getInstance())
            .provider(new GlobalScopeServices(startParameter.isContinuous(), AgentStatus.of(daemonParameters.shouldApplyInstrumentationAgent())))
            .build();

        globalServices.get(AgentInitializer.class).maybeConfigureInstrumentationAgent();

        // Force the user home services to be stopped first, the dependencies between the user home services and the global services are not preserved currently
        return runBuildAndCloseServices(startParameter, daemonParameters, globalServices.get(BuildExecuter.class), globalServices, globalServices.get(GradleUserHomeScopeServiceRegistry.class));
    }

    private Runnable runBuildInSingleUseDaemon(StartParameterInternal startParameter, DaemonParameters daemonParameters) {
        //(SF) this is a workaround until this story is completed. I'm hardcoding setting the idle timeout to be max X mins.
        //this way we avoid potential runaway daemons that steal resources on linux and break builds on windows.
        //We might leave that in if we decide it's a good idea for an extra safety net.
        int maxTimeout = 2 * 60 * 1000;
        if (daemonParameters.getIdleTimeout() > maxTimeout) {
            daemonParameters.setIdleTimeout(maxTimeout);
        }
        //end of workaround.

        // Create a client that will not match any existing daemons, so it will always startup a new one
        ServiceRegistry clientSharedServices = createGlobalClientServices(true);
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createSingleUseDaemonClientServices(clientSharedServices.get(OutputEventListener.class), daemonParameters, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return runBuildAndCloseServices(startParameter, daemonParameters, client, clientSharedServices, clientServices);
    }

    private ServiceRegistry createGlobalClientServices(boolean usingDaemon) {
        ServiceRegistryBuilder builder = ServiceRegistryBuilder.builder()
            .displayName("Daemon client global services")
            .parent(NativeServices.getInstance());
        if (usingDaemon) {
            builder.parent(basicServices);
        } else {
            builder.provider(new GlobalScopeServices(false, AgentStatus.disabled()));
        }
        return builder.provider(new DaemonClientGlobalServices()).build();
    }

    private Runnable runBuildAndCloseServices(StartParameterInternal startParameter, DaemonParameters daemonParameters, BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer, ServiceRegistry sharedServices, Object... stopBeforeSharedServices) {
        BuildActionParameters parameters = createBuildActionParameters(startParameter, daemonParameters);
        Stoppable stoppable = new CompositeStoppable().add(stopBeforeSharedServices).add(sharedServices);
        return new RunBuildAction(executer, startParameter, clientMetaData(), getBuildStartTime(), parameters, sharedServices, stoppable);
    }

    private BuildActionParameters createBuildActionParameters(StartParameter startParameter, DaemonParameters daemonParameters) {
        return new DefaultBuildActionParameters(
            daemonParameters.getEffectiveSystemProperties(),
            daemonParameters.getEnvironmentVariables(),
            SystemProperties.getInstance().getCurrentDir(),
            startParameter.getLogLevel(),
            daemonParameters.isEnabled(),
            ClassPath.EMPTY);
    }

    private long getBuildStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }
}
