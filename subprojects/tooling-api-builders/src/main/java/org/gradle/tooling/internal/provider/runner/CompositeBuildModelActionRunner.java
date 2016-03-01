/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.StartParameter;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Nullable;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.composite.*;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.*;
import org.gradle.tooling.composite.ProjectIdentity;
import org.gradle.tooling.internal.consumer.CancellationTokenInternal;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.protocol.CompositeBuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.DefaultBuildIdentity;
import org.gradle.tooling.internal.protocol.DefaultProjectIdentity;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CompositeBuildModelActionRunner implements CompositeBuildActionRunner {
    public void run(BuildAction action, BuildRequestContext requestContext, CompositeBuildActionParameters actionParameters, CompositeBuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }
        Class<?> modelType = resolveModelType((BuildModelAction) action);
        ProgressLoggerFactory progressLoggerFactory = buildController.getBuildScopeServices().get(ProgressLoggerFactory.class);
        Map<Object, Object> results = null;
        if (modelType != Void.class) {
            results = aggregateModels(modelType, actionParameters, requestContext.getCancellationToken(), progressLoggerFactory);
        } else {
            if (!((BuildModelAction) action).isRunTasks()) {
                throw new IllegalStateException("No tasks defined.");
            }
            executeTasks(action.getStartParameter(), actionParameters, requestContext.getCancellationToken(), progressLoggerFactory);
        }
        PayloadSerializer payloadSerializer = buildController.getBuildScopeServices().get(PayloadSerializer.class);
        buildController.setResult(new BuildActionResult(payloadSerializer.serialize(results), null));
    }

    private void executeTasks(StartParameter startParameter, CompositeBuildActionParameters actionParameters, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        CompositeParameters compositeParameters = actionParameters.getCompositeParameters();
        boolean buildFound = false;
        for (GradleParticipantBuild participant : compositeParameters.getBuilds()) {
            if (!participant.getProjectDir().getAbsolutePath().equals(compositeParameters.getCompositeTargetBuildRootDir().getAbsolutePath())) {
                continue;
            }
            buildFound = true;
            if (cancellationToken.isCancellationRequested()) {
                break;
            }
            ProjectConnection projectConnection = connect(participant, compositeParameters);
            try {
                BuildLauncher buildLauncher = projectConnection.newBuild();
                buildLauncher.withCancellationToken(new CancellationTokenAdapter(cancellationToken));
                buildLauncher.addProgressListener(new ProgressListenerToProgressLoggerAdapter(progressLoggerFactory));
                List<String> taskArgs = new ArrayList<String>();
                for (TaskExecutionRequest request : startParameter.getTaskRequests()) {
                    if (request.getProjectPath() == null) {
                        taskArgs.addAll(request.getArgs());
                    } else {
                        String projectPath = request.getProjectPath();
                        int index = 0;
                        for (String arg : request.getArgs()) {
                            if (index == 0) {
                                // add project path to first arg
                                taskArgs.add(projectPath + ":" + arg);
                            } else {
                                taskArgs.add(arg);
                            }
                            index++;
                        }
                    }
                }
                buildLauncher.forTasks(taskArgs.toArray(new String[0]));
                buildLauncher.run();
            } catch (GradleConnectionException e) {
                throw new CompositeBuildExceptionVersion1(e, new DefaultBuildIdentity(compositeParameters.getCompositeTargetBuildRootDir()));
            } finally {
                projectConnection.close();
            }
        }
        if (!buildFound) {
            throw new IllegalStateException("Build not part of composite");
        }
    }

    private Class<?> resolveModelType(BuildModelAction action) {
        final String requestedModelName = action.getModelName();
        try {
            return Cast.uncheckedCast(getClass().getClassLoader().loadClass(requestedModelName));
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Map<Object, Object> aggregateModels(Class<?> modelType, CompositeBuildActionParameters actionParameters, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        final Map<Object, Object> results = new HashMap<Object, Object>();
        final CompositeParameters compositeParameters = actionParameters.getCompositeParameters();
        results.putAll(fetchModels(compositeParameters.getBuilds(), modelType, cancellationToken, compositeParameters, progressLoggerFactory));
        return results;
    }

    private Map<Object, Object> fetchModels(List<GradleParticipantBuild> participantBuilds, Class<?> modelType, final BuildCancellationToken cancellationToken, CompositeParameters compositeParameters, final ProgressLoggerFactory progressLoggerFactory) {
        final Map<Object, Object> results = new HashMap<Object, Object>();
        for (GradleParticipantBuild participant : participantBuilds) {
            if (cancellationToken.isCancellationRequested()) {
                break;
            }
            ProjectConnection projectConnection = connect(participant, compositeParameters);
            File rootDir = participant.getProjectDir();
            DefaultBuildIdentity buildIdentity = new DefaultBuildIdentity(rootDir);
            try {
                if (modelType == BuildEnvironment.class) {
                    ProjectIdentity projectIdentity = new DefaultProjectIdentity(buildIdentity, rootDir, ":");
                    results.putAll(fetchPerBuildModels(projectIdentity, projectConnection, modelType, cancellationToken, progressLoggerFactory));
                } else {
                    results.putAll(fetchPerProjectModels(projectConnection, modelType, cancellationToken, progressLoggerFactory));
                }
            } catch (GradleConnectionException e) {
                throw new CompositeBuildExceptionVersion1(e, buildIdentity);
            } finally {
                projectConnection.close();
            }
        }
        return results;
    }

    private Map<Object, Object> fetchPerBuildModels(Object projectIdentity, ProjectConnection projectConnection, Class<?> modelType, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        Object result = fetchModel(projectConnection, modelType, cancellationToken, progressLoggerFactory);
        if(result != null) {
            return Collections.singletonMap(projectIdentity, result);
        }
        return Collections.emptyMap();
    }

    private Map<Object, Object> fetchPerProjectModels(ProjectConnection projectConnection, Class<?> modelType, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        Map<Object, Object> results = new HashMap<Object, Object>();
        BuildActionExecuter<Map<Object, Object>> buildActionExecuter = projectConnection.action(new FetchPerProjectModelAction(modelType.getName()));
        buildActionExecuter.withCancellationToken(new CancellationTokenAdapter(cancellationToken));
        buildActionExecuter.addProgressListener(new ProgressListenerToProgressLoggerAdapter(progressLoggerFactory));

        if (!cancellationToken.isCancellationRequested()) {
            for (Map.Entry<Object, Object> e : buildActionExecuter.run().entrySet()) {
                InternalProjectIdentity internalProjectIdentity = (InternalProjectIdentity) e.getKey();
                results.put(convertToProjectIdentity(internalProjectIdentity), e.getValue());
            }
        }
        return results;
    }

    private DefaultProjectIdentity convertToProjectIdentity(InternalProjectIdentity internalProjectIdentity) {
        return new DefaultProjectIdentity(new DefaultBuildIdentity(internalProjectIdentity.rootDir), internalProjectIdentity.rootDir, internalProjectIdentity.projectPath);
    }

    private static final class FetchPerProjectModelAction implements org.gradle.tooling.BuildAction<Map<Object, Object>> {
        private final String modelTypeName;
        private FetchPerProjectModelAction(String modelTypeName) {
            this.modelTypeName = modelTypeName;
        }

        @Override
        public Map<Object, Object> execute(BuildController controller) {
            Class<?> modelType;
            try {
                modelType = Class.forName(modelTypeName);
            } catch (ClassNotFoundException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            final Map<Object, Object> results = new HashMap<Object, Object>();
            fetchResults(modelType, results, controller, controller.getBuildModel().getRootProject(), controller.getBuildModel().getRootProject());
            return results;
        }

        private void fetchResults(Class<?> modelType, Map<Object, Object> results, BuildController controller, BasicGradleProject project, BasicGradleProject rootProject) {
            File rootDir = rootProject.getProjectDirectory();
            results.put(new InternalProjectIdentity(rootDir, project.getPath()), controller.getModel(project, modelType));
            for (BasicGradleProject child : project.getChildren()) {
                fetchResults(modelType, results, controller, child, rootProject);
            }
        }
    }

    private static final class InternalProjectIdentity implements Serializable {
        private final File rootDir;
        private final String projectPath;

        private InternalProjectIdentity(File rootDir, String projectPath) {
            this.rootDir = rootDir;
            this.projectPath = projectPath;
        }
    }

    @Nullable
    private <T> T fetchModel(ProjectConnection projectConnection, Class<T> modelType, BuildCancellationToken cancellationToken, ProgressLoggerFactory progressLoggerFactory) {
        ModelBuilder<T> modelBuilder = projectConnection.model(modelType);
        modelBuilder.withCancellationToken(new CancellationTokenAdapter(cancellationToken));
        modelBuilder.addProgressListener(new ProgressListenerToProgressLoggerAdapter(progressLoggerFactory));
        if (cancellationToken.isCancellationRequested()) {
            return null;
        }
        return modelBuilder.get();
    }

    private ProjectConnection connect(GradleParticipantBuild build, CompositeParameters compositeParameters) {
        DefaultGradleConnector connector = getInternalConnector();
        File gradleUserHomeDir = compositeParameters.getGradleUserHomeDir();
        File daemonBaseDir = compositeParameters.getDaemonBaseDir();
        Integer daemonMaxIdleTimeValue = compositeParameters.getDaemonMaxIdleTimeValue();
        TimeUnit daemonMaxIdleTimeUnits = compositeParameters.getDaemonMaxIdleTimeUnits();
        Boolean embeddedParticipants = compositeParameters.isEmbeddedParticipants();

        if (gradleUserHomeDir != null) {
            connector.useGradleUserHomeDir(gradleUserHomeDir);
        }
        if (daemonBaseDir != null) {
            connector.daemonBaseDir(daemonBaseDir);
        }
        if (daemonMaxIdleTimeValue != null && daemonMaxIdleTimeUnits != null) {
            connector.daemonMaxIdleTime(daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits);
        }
        connector.searchUpwards(false);
        connector.forProjectDirectory(build.getProjectDir());

        if (embeddedParticipants) {
            connector.embedded(true);
            connector.useClasspathDistribution();
            return connector.connect();
        } else {
            return configureDistribution(connector, build).connect();
        }
    }

    private DefaultGradleConnector getInternalConnector() {
        return (DefaultGradleConnector) GradleConnector.newConnector();
    }

    private GradleConnector configureDistribution(GradleConnector connector, GradleParticipantBuild build) {
        if (build.getGradleDistribution() == null) {
            if (build.getGradleHome() == null) {
                if (build.getGradleVersion() == null) {
                    connector.useBuildDistribution();
                } else {
                    connector.useGradleVersion(build.getGradleVersion());
                }
            } else {
                connector.useInstallation(build.getGradleHome());
            }
        } else {
            connector.useDistribution(build.getGradleDistribution());
        }

        return connector;
    }

    private final static class CancellationTokenAdapter implements CancellationToken, CancellationTokenInternal {
        private final BuildCancellationToken token;

        private CancellationTokenAdapter(BuildCancellationToken token) {
            this.token = token;
        }

        public boolean isCancellationRequested() {
            return token.isCancellationRequested();
        }

        public BuildCancellationToken getToken() {
            return token;
        }
    }

}

