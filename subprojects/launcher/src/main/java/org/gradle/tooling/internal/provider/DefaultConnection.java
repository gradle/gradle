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
import org.gradle.api.internal.Factory;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientServices;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.server.DaemonParameters;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEventRenderer;
import org.gradle.tooling.internal.DefaultBuildEnvironment;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.input.AdaptedOperationParameters;
import org.gradle.tooling.internal.provider.input.ProviderOperationParameters;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class DefaultConnection implements ConnectionVersion4 {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final EmbeddedExecuterSupport embeddedExecuterSupport;

    public DefaultConnection() {
        LOGGER.debug("Using tooling API provider version {}.", GradleVersion.current().getVersion());
        //embedded use of the tooling api is not supported publicly so we don't care about its thread safety
        //we can keep still keep this state:
        embeddedExecuterSupport = new EmbeddedExecuterSupport();
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

    public void executeBuild(final BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        run(new ExecuteBuildAction(buildParameters.getTasks()), new AdaptedOperationParameters(operationParameters));
    }

    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        return getModelInternal(type, new AdaptedOperationParameters(operationParameters));
    }

    public <T> T getModelInternal(Class<T> type, ProviderOperationParameters operationParameters) {
        if (type == InternalBuildEnvironment.class) {
            //we don't really need to launch gradle to acquire this information, TODO SF refactor
            DaemonClientServices services = daemonClientServices(operationParameters);
            DaemonContext context = services.get(DaemonContext.class);
            DefaultBuildEnvironment out = new DefaultBuildEnvironment(GradleVersion.current().getVersion(), context.getJavaHome(), context.getDaemonOpts());
            return type.cast(out);
        }
        DelegatingBuildModelAction<T> action = new DelegatingBuildModelAction<T>(type);
        return run(action, operationParameters);
    }

    private <T> T run(GradleLauncherAction<T> action, ProviderOperationParameters operationParameters) {
        GradleLauncherActionExecuter<ProviderOperationParameters> executer = createExecuter(operationParameters);
        ConfiguringBuildAction<T> configuringAction = new ConfiguringBuildAction<T>(operationParameters.getGradleUserHomeDir(),
                operationParameters.getProjectDir(), operationParameters.isSearchUpwards(), operationParameters.getVerboseLogging(), action);
        return executer.execute(configuringAction, operationParameters);
    }

    private GradleLauncherActionExecuter<ProviderOperationParameters> createExecuter(ProviderOperationParameters operationParameters) {
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            return embeddedExecuterSupport.getExecuter();
        } else {
            DaemonClientServices clientServices = daemonClientServices(operationParameters);
            DaemonClient client = clientServices.get(DaemonClient.class);

            GradleLauncherActionExecuter<ProviderOperationParameters> executer = new DaemonGradleLauncherActionExecuter(client, clientServices.getDaemonParameters());

            Factory<LoggingManagerInternal> loggingManagerFactory = clientServices.getLoggingServices().getFactory(LoggingManagerInternal.class);
            return new LoggingBridgingGradleLauncherActionExecuter(executer, loggingManagerFactory);
        }
    }

    private DaemonClientServices daemonClientServices(ProviderOperationParameters operationParameters) {
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();

        if (operationParameters.getVerboseLogging()) {
            loggingServices.get(OutputEventRenderer.class).configure(LogLevel.DEBUG);
        }

        File gradleUserHomeDir = GUtil.elvis(operationParameters.getGradleUserHomeDir(), StartParameter.DEFAULT_GRADLE_USER_HOME);
        DaemonParameters parameters = new DaemonParameters();
        boolean searchUpwards = operationParameters.isSearchUpwards() != null ? operationParameters.isSearchUpwards() : true;
        parameters.configureFromBuildDir(operationParameters.getProjectDir(), searchUpwards);
        parameters.configureFromGradleUserHome(gradleUserHomeDir);
        parameters.configureFromSystemProperties(System.getProperties());
        if (operationParameters.getDaemonMaxIdleTimeValue() != null && operationParameters.getDaemonMaxIdleTimeUnits() != null) {
            int idleTimeout = (int) operationParameters.getDaemonMaxIdleTimeUnits().toMillis(operationParameters.getDaemonMaxIdleTimeValue());
            parameters.setIdleTimeout(idleTimeout);
        }
        return new DaemonClientServices(loggingServices, parameters, operationParameters.getStandardInput());
    }
}