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

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.*;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConnection implements ConnectionVersion4 {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final ServiceRegistry loggingServices;

    public DefaultConnection() {
        LOGGER.debug("Using tooling API provider version {}.", GradleVersion.current().getVersion());
        loggingServices = new LoggingServiceRegistry(false);
        GradleLauncher.injectCustomFactory(new DefaultGradleLauncherFactory(loggingServices));
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

    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        StartParameter startParameter = new ConnectionToStartParametersConverter().convert(operationParameters);
        startParameter.setTaskNames(buildParameters.getTasks());

        GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);
        run(operationParameters, gradleLauncher, new BuildAction() {
            public BuildResult execute(GradleLauncher gradleLauncher) {
                return gradleLauncher.run();
            }
        });
    }

    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) {
        if (!type.isAssignableFrom(EclipseProjectVersion3.class)) {
            throw new UnsupportedOperationException(String.format("Cannot build model of type '%s'.", type.getSimpleName()));
        }

        StartParameter startParameter = new ConnectionToStartParametersConverter().convert(operationParameters);

        GradleLauncher gradleLauncher = GradleLauncher.newInstance(startParameter);

        boolean projectDependenciesOnly = !EclipseProjectVersion3.class.isAssignableFrom(type);
        boolean includeTasks = BuildableProjectVersion1.class.isAssignableFrom(type);

        ModelBuildingAdapter adapter = new ModelBuildingAdapter(
                new EclipsePluginApplier(), new ModelBuilder(includeTasks, projectDependenciesOnly));
        gradleLauncher.addListener(adapter);

        run(operationParameters, gradleLauncher, new BuildAction() {
            public BuildResult execute(GradleLauncher gradleLauncher) {
                return gradleLauncher.getBuildAnalysis();
            }
        });

        return type.cast(adapter.getProject());
    }

    private void run(LongRunningOperationParametersVersion1 operationParameters, GradleLauncher gradleLauncher, BuildAction action) {
        if (operationParameters.getStandardOutput() != null) {
            gradleLauncher.addStandardOutputListener(new StreamBackedStandardOutputListener(operationParameters.getStandardOutput()));
        }
        if (operationParameters.getStandardError() != null) {
            gradleLauncher.addStandardErrorListener(new StreamBackedStandardOutputListener(operationParameters.getStandardError()));
        }
        ProgressListenerVersion1 progressListener = operationParameters.getProgressListener();
        LoggingOutputInternal loggingOutput = loggingServices.get(LoggingOutputInternal.class);
        OutputEventListenerAdapter listener = new OutputEventListenerAdapter(progressListener);
        loggingOutput.addOutputEventListener(listener);
        try {
            BuildResult result = action.execute(gradleLauncher);
            if (result.getFailure() != null) {
                throw new BuildExceptionVersion1(result.getFailure());
            }
        } finally {
            loggingOutput.removeOutputEventListener(listener);
        }
    }

    private interface BuildAction {
        BuildResult execute(GradleLauncher gradleLauncher);
    }

    private static class OutputEventListenerAdapter implements OutputEventListener {
        private final ProgressListenerVersion1 progressListener;

        public OutputEventListenerAdapter(ProgressListenerVersion1 progressListener) {
            this.progressListener = progressListener;
        }

        public void onOutput(OutputEvent event) {
            if (event instanceof ProgressStartEvent) {
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                progressListener.onOperationStart(startEvent.getDescription());
            } else if (event instanceof ProgressCompleteEvent) {
                progressListener.onOperationEnd();
            }
        }
    }
}
