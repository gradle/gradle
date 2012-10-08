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
package org.gradle.tooling.internal.provider;

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.internal.Factory;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientServices;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEventRenderer;
import org.gradle.logging.internal.logback.SimpleLogbackLoggingConfigurer;
import org.gradle.process.internal.streams.SafeStreams;
import org.gradle.tooling.internal.build.DefaultBuildEnvironment;
import org.gradle.tooling.internal.consumer.protocoladapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.connection.*;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class DefaultConnection implements InternalConnection, BuildActionRunner, ConfigurableConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final EmbeddedExecuterSupport embeddedExecuterSupport;
    private final SimpleLogbackLoggingConfigurer loggingConfigurer = new SimpleLogbackLoggingConfigurer();
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();

    public DefaultConnection() {
        LOGGER.debug("Provider implementation created.");
        //embedded use of the tooling api is not supported publicly so we don't care about its thread safety
        //we can still keep this state:
        embeddedExecuterSupport = new EmbeddedExecuterSupport();
        LOGGER.debug("Embedded executer support created.");
    }

    public void configure(ConnectionParameters parameters) {
        ProviderConnectionParameters providerConnectionParameters = new ProtocolToModelAdapter().adapt(ProviderConnectionParameters.class, parameters);
        configureLogging(providerConnectionParameters.getVerboseLogging());
    }

    public void configureLogging(boolean verboseLogging) {
        LogLevel providerLogLevel = verboseLogging? LogLevel.DEBUG : LogLevel.INFO;
        LOGGER.debug("Configuring logging to level: {}", providerLogLevel);
        loggingConfigurer.configure(providerLogLevel);
    }

    public ConnectionMetaDataVersion1 getMetaData() {
        return new ConnectionMetaDataVersion1() {
            public String getVersion() {
                return GradleVersion.current().getVersion();
            }

            public String getDisplayName() {
                return String.format("Gradle %s", getVersion());
            }
        };
    }

    public void stop() {
    }

    @Deprecated
    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        logTargetVersion();
        run(Void.class, new AdaptedOperationParameters(operationParameters, buildParameters.getTasks()));
    }

    @Deprecated
    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 parameters) {
        logTargetVersion();
        return run(type, new AdaptedOperationParameters(parameters));
    }

    @Deprecated
    public <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 parameters) {
        logTargetVersion();
        return run(type, new AdaptedOperationParameters(parameters));
    }

    public <T> BuildResult<T> run(Class<T> type, BuildParameters buildParameters) throws UnsupportedOperationException, IllegalStateException {
        logTargetVersion();
        ProviderOperationParameters providerParameters = adapter.adapt(ProviderOperationParameters.class, buildParameters, BuildLogLevelMixIn.class);
        T result = run(type, providerParameters);
        return new ProviderBuildResult<T>(result);
    }

    private <T> T run(Class<T> type, ProviderOperationParameters providerParameters) {
        List<String> tasks = providerParameters.getTasks();
        if (type.equals(Void.class) && tasks == null) {
            throw new IllegalArgumentException("No model type or tasks specified.");
        }
        if (type == InternalBuildEnvironment.class) {
            //we don't really need to launch the daemon to acquire information needed for BuildEnvironment
            if (tasks != null) {
                throw new IllegalArgumentException("Cannot run tasks and fetch the build environment model.");
            }
            DaemonParameters daemonParameters = init(providerParameters);
            DefaultBuildEnvironment out = new DefaultBuildEnvironment(
                    GradleVersion.current().getVersion(),
                    daemonParameters.getEffectiveJavaHome(),
                    daemonParameters.getEffectiveJvmArgs());

            return type.cast(out);
        }

        DelegatingBuildModelAction<T> action = new DelegatingBuildModelAction<T>(type, tasks != null);
        return run(action, providerParameters);
    }

    private void logTargetVersion() {
        LOGGER.info("Tooling API uses target gradle version:" + " {}.", GradleVersion.current().getVersion());
    }

    private <T> T run(GradleLauncherAction<T> action, ProviderOperationParameters operationParameters) {
        GradleLauncherActionExecuter<ProviderOperationParameters> executer = createExecuter(operationParameters);
        ConfiguringBuildAction<T> configuringAction = new ConfiguringBuildAction<T>(operationParameters, action);
        return executer.execute(configuringAction, operationParameters);
    }

    private GradleLauncherActionExecuter<ProviderOperationParameters> createExecuter(ProviderOperationParameters operationParameters) {
        LoggingServiceRegistry loggingServices;
        DaemonParameters daemonParams = init(operationParameters);
        GradleLauncherActionExecuter<BuildActionParameters> executer;
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            loggingServices = embeddedExecuterSupport.getLoggingServices();
            executer = embeddedExecuterSupport.getExecuter();
        } else {
            loggingServices = embeddedExecuterSupport.getLoggingServices().newLogging();
            loggingServices.get(OutputEventRenderer.class).configure(operationParameters.getBuildLogLevel());
            DaemonClientServices clientServices = new DaemonClientServices(loggingServices, daemonParams, operationParameters.getStandardInput(SafeStreams.emptyInput()));
            executer = clientServices.get(DaemonClient.class);
        }
        Factory<LoggingManagerInternal> loggingManagerFactory = loggingServices.getFactory(LoggingManagerInternal.class);
        return new LoggingBridgingGradleLauncherActionExecuter(new DaemonGradleLauncherActionExecuter(executer, daemonParams), loggingManagerFactory);
    }

    private DaemonParameters init(ProviderOperationParameters operationParameters) {
        File gradleUserHomeDir = GUtil.elvis(operationParameters.getGradleUserHomeDir(), StartParameter.DEFAULT_GRADLE_USER_HOME);
        DaemonParameters daemonParams = new DaemonParameters();

        boolean searchUpwards = operationParameters.isSearchUpwards() != null ? operationParameters.isSearchUpwards() : true;
        daemonParams.configureFromBuildDir(operationParameters.getProjectDir(), searchUpwards);
        daemonParams.configureFromGradleUserHome(gradleUserHomeDir);
        daemonParams.configureFromSystemProperties(System.getProperties());

        //override the params with the explicit settings provided by the tooling api
        List<String> defaultJvmArgs = daemonParams.getAllJvmArgs();
        daemonParams.setJvmArgs(operationParameters.getJvmArguments(defaultJvmArgs));
        File defaultJavaHome = daemonParams.getEffectiveJavaHome();
        daemonParams.setJavaHome(operationParameters.getJavaHome(defaultJavaHome));

        if (operationParameters.getDaemonMaxIdleTimeValue() != null && operationParameters.getDaemonMaxIdleTimeUnits() != null) {
            int idleTimeout = (int) operationParameters.getDaemonMaxIdleTimeUnits().toMillis(operationParameters.getDaemonMaxIdleTimeValue());
            daemonParams.setIdleTimeout(idleTimeout);
        }
        return daemonParams;
    }

}