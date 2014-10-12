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

import org.gradle.StartParameter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.LayoutCommandLineConverter;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.launcher.cli.converter.DaemonCommandLineConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.PropertiesToDaemonParametersConverter;
import org.gradle.launcher.cli.converter.PropertiesToStartParameterConverter;
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.configuration.CurrentProcess;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.ForegroundDaemonConfiguration;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.logging.internal.OutputEventListener;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

class BuildActionsFactory implements CommandLineAction {

    private static final String FOREGROUND = "foreground";
    private static final String STOP = "stop";

    private final ServiceRegistry loggingServices;
    private final LayoutCommandLineConverter layoutConverter;

    private final SystemPropertiesCommandLineConverter propertiesConverter;
    private final LayoutToPropertiesConverter layoutToPropertiesConverter;

    private final PropertiesToStartParameterConverter propertiesToStartParameterConverter;
    private final DefaultCommandLineConverter commandLineConverter;

    private final DaemonCommandLineConverter daemonConverter;
    private final PropertiesToDaemonParametersConverter propertiesToDaemonParametersConverter;

    BuildActionsFactory(ServiceRegistry loggingServices) {
        this(loggingServices, new DefaultCommandLineConverter());
    }

    BuildActionsFactory(ServiceRegistry loggingServices, DefaultCommandLineConverter commandLineConverter,
                        DaemonCommandLineConverter daemonConverter, LayoutCommandLineConverter layoutConverter,
                        SystemPropertiesCommandLineConverter propertiesConverter,
                        LayoutToPropertiesConverter layoutToPropertiesConverter,
                        PropertiesToStartParameterConverter propertiesToStartParameterConverter,
                        PropertiesToDaemonParametersConverter propertiesToDaemonParametersConverter) {
        this.loggingServices = loggingServices;
        this.commandLineConverter = commandLineConverter;
        this.daemonConverter = daemonConverter;
        this.layoutConverter = layoutConverter;
        this.propertiesConverter = propertiesConverter;
        this.layoutToPropertiesConverter = layoutToPropertiesConverter;
        this.propertiesToStartParameterConverter = propertiesToStartParameterConverter;
        this.propertiesToDaemonParametersConverter = propertiesToDaemonParametersConverter;
    }

    private BuildActionsFactory(ServiceRegistry loggingServices, DefaultCommandLineConverter commandLineConverter) {
        this(loggingServices, commandLineConverter, new DaemonCommandLineConverter(),
                commandLineConverter.getLayoutConverter(), commandLineConverter.getSystemPropertiesConverter(),
                new LayoutToPropertiesConverter(), new PropertiesToStartParameterConverter(), new PropertiesToDaemonParametersConverter());
    }

    public void configureCommandLineParser(CommandLineParser parser) {
        commandLineConverter.configure(parser);
        daemonConverter.configure(parser);

        parser.option(FOREGROUND).hasDescription("Starts the Gradle daemon in the foreground.").incubating();
        parser.option(STOP).hasDescription("Stops the Gradle daemon if it is running.");
    }

    public Runnable createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
        BuildLayoutParameters layout = new BuildLayoutParameters();
        layoutConverter.convert(commandLine, layout);

        Map<String, String> properties = new HashMap<String, String>();
        layoutToPropertiesConverter.convert(layout, properties);
        propertiesConverter.convert(commandLine, properties);

        StartParameter startParameter = new StartParameter();
        propertiesToStartParameterConverter.convert(properties, startParameter);
        commandLineConverter.convert(commandLine, startParameter);

        DaemonParameters daemonParameters = new DaemonParameters(layout, startParameter.getSystemPropertiesArgs());
        propertiesToDaemonParametersConverter.convert(properties, daemonParameters);
        daemonConverter.convert(commandLine, daemonParameters);

        if (commandLine.hasOption(STOP)) {
            return stopAllDaemons(daemonParameters, loggingServices);
        }
        if (commandLine.hasOption(FOREGROUND)) {
            ForegroundDaemonConfiguration conf = new ForegroundDaemonConfiguration(
                    daemonParameters.getUid(), daemonParameters.getBaseDir(), daemonParameters.getIdleTimeout());
            return new ForegroundDaemonAction(loggingServices, conf);
        }
        if (daemonParameters.isEnabled()) {
            return runBuildWithDaemon(startParameter, daemonParameters, loggingServices);
        }
        if (canUseCurrentProcess(daemonParameters)) {
            return runBuildInProcess(startParameter, daemonParameters, loggingServices);
        }
        return runBuildInSingleUseDaemon(startParameter, daemonParameters, loggingServices);
    }

    private Runnable stopAllDaemons(DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        ServiceRegistry clientSharedServices = createGlobalClientServices();
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createStopDaemonServices(loggingServices.get(OutputEventListener.class), daemonParameters);
        DaemonStopClient stopClient = clientServices.get(DaemonStopClient.class);
        return new StopDaemonAction(stopClient);
    }

    private Runnable runBuildWithDaemon(StartParameter startParameter, DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        // Create a client that will match based on the daemon startup parameters.
        ServiceRegistry clientSharedServices = createGlobalClientServices();
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createBuildClientServices(loggingServices.get(OutputEventListener.class), daemonParameters, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return daemonBuildAction(startParameter, daemonParameters, client);
    }

    private boolean canUseCurrentProcess(DaemonParameters requiredBuildParameters) {
        CurrentProcess currentProcess = new CurrentProcess();
        return currentProcess.configureForBuild(requiredBuildParameters);
    }

    private Runnable runBuildInProcess(StartParameter startParameter, DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        ServiceRegistry globalServices = ServiceRegistryBuilder.builder()
                .displayName("Global services")
                .parent(loggingServices)
                .parent(NativeServices.getInstance())
                .provider(new GlobalScopeServices(false))
                .build();
        InProcessBuildActionExecuter executer = new InProcessBuildActionExecuter(globalServices.get(GradleLauncherFactory.class));
        return daemonBuildAction(startParameter, daemonParameters, executer);
    }

    private Runnable runBuildInSingleUseDaemon(StartParameter startParameter, DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        //(SF) this is a workaround until this story is completed. I'm hardcoding setting the idle timeout to be max X mins.
        //this way we avoid potential runaway daemons that steal resources on linux and break builds on windows.
        //We might leave that in if we decide it's a good idea for an extra safety net.
        int maxTimeout = 2 * 60 * 1000;
        if (daemonParameters.getIdleTimeout() > maxTimeout) {
            daemonParameters.setIdleTimeout(maxTimeout);
        }
        //end of workaround.

        // Create a client that will not match any existing daemons, so it will always startup a new one
        ServiceRegistry clientSharedServices = createGlobalClientServices();
        ServiceRegistry clientServices = clientSharedServices.get(DaemonClientFactory.class).createSingleUseDaemonClientServices(loggingServices.get(OutputEventListener.class), daemonParameters, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return daemonBuildAction(startParameter, daemonParameters, client);
    }

    private ServiceRegistry createGlobalClientServices() {
        return ServiceRegistryBuilder.builder()
                .displayName("Daemon client global services")
                .parent(NativeServices.getInstance())
                .provider(new GlobalScopeServices(false))
                .provider(new DaemonClientGlobalServices())
                .build();
    }

    private Runnable daemonBuildAction(StartParameter startParameter, DaemonParameters daemonParameters, BuildActionExecuter<BuildActionParameters> executer) {
        return new RunBuildAction(executer, startParameter, SystemProperties.getCurrentDir(), clientMetaData(), getBuildStartTime(), daemonParameters.getEffectiveSystemProperties(), System.getenv());
    }

    private long getBuildStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }
}
