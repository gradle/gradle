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
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientServices;
import org.gradle.launcher.daemon.server.DaemonParameters;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.tooling.internal.CompatibilityChecker;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.model.IncompatibleVersionException;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

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
        run(new ExecuteBuildAction(buildParameters.getTasks()), operationParameters);
    }

    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        GradleLauncherAction<ProjectVersion3> action = new DelegatingBuildModelAction(type);
        return run(action, operationParameters);
    }

    private <T> T run(GradleLauncherAction<T> action, BuildOperationParametersVersion1 operationParameters) {
        GradleLauncherActionExecuter<BuildOperationParametersVersion1> executer = createExecuter(operationParameters);
        ConfiguringBuildAction<T> configuringAction = new ConfiguringBuildAction<T>(operationParameters.getGradleUserHomeDir(), operationParameters.getProjectDir(), operationParameters.isSearchUpwards(), action);
        return executer.execute(configuringAction, operationParameters);
    }

    private GradleLauncherActionExecuter<BuildOperationParametersVersion1> createExecuter(BuildOperationParametersVersion1 operationParameters) {
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            return embeddedExecuterSupport.getExecuter();
        } else {
            LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
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
            DaemonClientServices clientServices = new DaemonClientServices(loggingServices, parameters, safeStandardInput(operationParameters));
            DaemonClient client = clientServices.get(DaemonClient.class);
            GradleLauncherActionExecuter<BuildOperationParametersVersion1> executer = new DaemonGradleLauncherActionExecuter(client, parameters);

            Factory<LoggingManagerInternal> loggingManagerFactory = loggingServices.getFactory(LoggingManagerInternal.class);
            return new LoggingBridgingGradleLauncherActionExecuter(executer, loggingManagerFactory);
        }
    }

    private InputStream safeStandardInput(BuildOperationParametersVersion1 operationParameters) {
        InputStream is;
        try {
            new CompatibilityChecker(operationParameters).assertSupports("getStandardInput");
            is = operationParameters.getStandardInput();
        } catch (IncompatibleVersionException e) {
            return null;
        }

        if (is == null) {
            //Tooling api means embedded use. We don't want to consume standard input if we don't own the process.
            //Hence we use a dummy input stream by default
            return new ByteArrayInputStream(new byte[0]);
        }
        return is;
    }
}