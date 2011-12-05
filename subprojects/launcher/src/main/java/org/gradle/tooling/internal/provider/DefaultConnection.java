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
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientServices;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.server.DaemonIdleTimeout;
import org.gradle.launcher.daemon.server.DaemonJvmOptions;
import org.gradle.launcher.exec.GradleLauncherActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class DefaultConnection implements ConnectionVersion4 {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final ServiceRegistry loggingServices;
    private final GradleLauncherFactory gradleLauncherFactory;

    public DefaultConnection() {
        LOGGER.debug("Using tooling API provider version {}.", GradleVersion.current().getVersion());

        //below is only used for embedded use - TODO SF refactor the code to make it explicit through the design
        loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
        gradleLauncherFactory = new DefaultGradleLauncherFactory(loggingServices);
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
        GradleLauncherActionExecuter<BuildOperationParametersVersion1> executer;
        Factory<LoggingManagerInternal> loggingManagerFactory;
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            executer = new EmbeddedGradleLauncherActionExecuter(gradleLauncherFactory);
            //for embedded daemon (internal use only), we will use the same logging services that were used to construct gradleLauncherFactory:
            loggingManagerFactory = loggingServices.getFactory(LoggingManagerInternal.class);
        } else {
            File gradleUserHomeDir = GUtil.elvis(operationParameters.getGradleUserHomeDir(), StartParameter.DEFAULT_GRADLE_USER_HOME);
            File daemonBaseDir = DaemonDir.calculateDirectoryViaPropertiesOrUseDefaultInGradleUserHome(System.getProperties(), gradleUserHomeDir);
            List<String> daemonOpts = DaemonJvmOptions.getFromEnvironmentVariable();
            //using 'fresh' logging services registry per operation to avoid concurrency issues
            LoggingServiceRegistry freshLoggingServices = LoggingServiceRegistry.newEmbeddableLogging();
            DaemonClientServices clientServices = new DaemonClientServices(freshLoggingServices, daemonBaseDir, daemonOpts, getIdleTimeout(operationParameters));
            DaemonClient client = clientServices.get(DaemonClient.class);
            executer = new DaemonGradleLauncherActionExecuter(client);
            loggingManagerFactory = freshLoggingServices.getFactory(LoggingManagerInternal.class);
        }
        return new LoggingBridgingGradleLauncherActionExecuter(executer, loggingManagerFactory);
    }

    private int getIdleTimeout(BuildOperationParametersVersion1 operationParameters) {
        if (operationParameters.getDaemonMaxIdleTimeValue() != null && operationParameters.getDaemonMaxIdleTimeUnits() != null) {
            return (int) operationParameters.getDaemonMaxIdleTimeUnits().toMillis(operationParameters.getDaemonMaxIdleTimeValue());
        } else {
            return DaemonIdleTimeout.DEFAULT_IDLE_TIMEOUT;
        }
    }
}