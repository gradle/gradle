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
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEventRenderer;
import org.gradle.logging.internal.slf4j.SimpleSlf4jLoggingConfigurer;
import org.gradle.tooling.internal.build.DefaultBuildEnvironment;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.input.AdaptedOperationParameters;
import org.gradle.tooling.internal.provider.input.ProviderOperationParameters;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class DefaultConnection implements InternalConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final EmbeddedExecuterSupport embeddedExecuterSupport;
    private final SimpleSlf4jLoggingConfigurer loggingConfigurer = new SimpleSlf4jLoggingConfigurer();

    public DefaultConnection() {
        LOGGER.debug("Provider implementation created.");
        //embedded use of the tooling api is not supported publicly so we don't care about its thread safety
        //we can keep still keep this state:
        embeddedExecuterSupport = new EmbeddedExecuterSupport();
        LOGGER.debug("Embedded executer support created.");
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

    public void executeBuild(final BuildParametersVersion1 buildParameters,
                             BuildOperationParametersVersion1 operationParameters) {
        logTargetVersion();
        AdaptedOperationParameters adaptedParams = new AdaptedOperationParameters(operationParameters, buildParameters);
        run(new ExecuteBuildAction(), adaptedParams);
    }

    private void logTargetVersion() {
        LOGGER.info("Tooling API uses target gradle version:" + " {}.", GradleVersion.current().getVersion());
    }

    @Deprecated //getTheModel method has much convenient interface, e.g. avoids locking to building only models of a specific type
    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 parameters) {
        return getTheModel(type, parameters);
    }

    public <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 parameters) {
        logTargetVersion();
        ProviderOperationParameters adaptedParameters = new AdaptedOperationParameters(parameters);
        if (type == InternalBuildEnvironment.class) {

            //we don't really need to launch gradle to acquire information needed for BuildEnvironment
            DaemonParameters daemonParameters = init(adaptedParameters);
            DefaultBuildEnvironment out = new DefaultBuildEnvironment(
                GradleVersion.current().getVersion(),
                daemonParameters.getEffectiveJavaHome(),
                daemonParameters.getEffectiveJvmArgs());

            return type.cast(out);
        }
        DelegatingBuildModelAction<T> action = new DelegatingBuildModelAction<T>(type);
        return run(action, adaptedParameters);
    }

    private <T> T run(GradleLauncherAction<T> action, ProviderOperationParameters operationParameters) {
        GradleLauncherActionExecuter<ProviderOperationParameters> executer = createExecuter(operationParameters);
        ConfiguringBuildAction<T> configuringAction = new ConfiguringBuildAction<T>(operationParameters, action);
        return executer.execute(configuringAction, operationParameters);
    }

    private GradleLauncherActionExecuter<ProviderOperationParameters> createExecuter(ProviderOperationParameters operationParameters) {
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            return embeddedExecuterSupport.getExecuter();
        } else {
            LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();

            loggingServices.get(OutputEventRenderer.class).configure(operationParameters.getBuildLogLevel());

            DaemonParameters daemonParams = init(operationParameters);
            DaemonClientServices clientServices = new DaemonClientServices(loggingServices, daemonParams, operationParameters.getStandardInput());
            DaemonClient client = clientServices.get(DaemonClient.class);

            GradleLauncherActionExecuter<ProviderOperationParameters> executer = new DaemonGradleLauncherActionExecuter(client, clientServices.getDaemonParameters());

            Factory<LoggingManagerInternal> loggingManagerFactory = clientServices.getLoggingServices().getFactory(LoggingManagerInternal.class);
            return new LoggingBridgingGradleLauncherActionExecuter(executer, loggingManagerFactory);
        }
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