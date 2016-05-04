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

package org.gradle.tooling.internal.connection;

import com.google.common.collect.Lists;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.converters.BuildInvocationsConverter;
import org.gradle.tooling.internal.consumer.converters.FixedBuildIdentifierProvider;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HasGradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.BuildInvocations;
import org.gradle.tooling.model.gradle.ProjectPublications;
import org.gradle.util.GradleVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolingClientCompositeModelBuilder<T> {
    private static final GradleVersion USE_CUSTOM_MODEL_ACTION_VERSION = GradleVersion.version("1.12");

    private final ConsumerOperationParameters operationParameters;
    private final ToolingClientCompositeUtil util;
    private final Class<T> modelType;
    private final List<CompositeModelResultsBuilder> builders = Lists.newArrayList();
    private final ProtocolToModelAdapter protocolToModelAdapter = new ProtocolToModelAdapter();

    ToolingClientCompositeModelBuilder(final Class<T> modelType, ConsumerOperationParameters operationParameters) {
        this.modelType = modelType;

        builders.add(new HierarchicalModelResultsBuilder());
        builders.add(new BuildInvocationsModelResultsBuilder());
        builders.add(new ProjectPublicationsModelResultBuilder());
        builders.add(new PerBuildModelResultsBuilder());
        this.util = new ToolingClientCompositeUtil(operationParameters);
        this.operationParameters = operationParameters;
    }

    public Iterable<ModelResult<T>> get() throws GradleConnectionException, IllegalStateException {
        final List<ModelResult<T>> results = Lists.newArrayList();

        for (GradleParticipantBuild participant : operationParameters.getBuilds()) {
            ParticipantConnector participantConnector = util.createParticipantConnector(participant);
            try {
                final List<ModelResult<T>> participantResults = buildResultsForParticipant(participantConnector);
                results.addAll(participantResults);
            } catch (GradleConnectionException e) {
                results.add(new DefaultFailedModelResult<T>(participantConnector.toBuildIdentifier(), e));
            }
        }
        return results;
    }

    private List<ModelResult<T>> buildResultsForParticipant(ParticipantConnector participant) throws GradleConnectionException {
        for (CompositeModelResultsBuilder builder : builders) {
            if (builder.canBuild(participant)) {
                final List<ModelResult<T>> participantResults = Lists.newArrayList();
                builder.addModelResults(participant, participantResults);
                return participantResults;
            }
        }
        throw new GradleConnectionException("Not a supported model type for this participant: " + modelType.getCanonicalName());
    }

    private abstract class CompositeModelResultsBuilder {
        public abstract boolean canBuild(ParticipantConnector participant);

        public abstract void addModelResults(ParticipantConnector participant, List<ModelResult<T>> results);

        protected DefaultModelResult<T> createModelResult(T value) {
            return new DefaultModelResult<T>(value);
        }

        protected <V> V getProjectModel(ParticipantConnector build, Class<V> modelType) throws GradleConnectionException {
            ProjectConnection connection = build.connect();
            try {
                ModelBuilder<V> modelBuilder = connection.model(modelType);
                util.configureRequest(modelBuilder);
                return modelBuilder.get();
            } finally {
                connection.close();
            }
        }
    }

    /**
     * Builds results for a 'per-build' model, simply requesting the model from the root project.
     */
    private class PerBuildModelResultsBuilder extends CompositeModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            // This is the fallback for any unknown model, and also for GradleBuild, IdeaProject and BuildInvocations
            return true;
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            T model = getProjectModel(participant, modelType);
            modelResults.add(createModelResult(model));
        }
    }

    /**
     * Builds results for a 'hierarchical' model, that provides both the Gradle project structure and the model for each subproject.
     */
    private class HierarchicalModelResultsBuilder extends CompositeModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return hasProjectHierarchy(modelType);
        }

        private boolean hasProjectHierarchy(Class<T> modelType) {
            return HierarchicalElement.class.isAssignableFrom(modelType)
                && (GradleProject.class.isAssignableFrom(modelType) || HasGradleProject.class.isAssignableFrom(modelType));
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            addResultsFromHierarchicalModel(participant, modelResults);
        }

        private void addResultsFromHierarchicalModel(ParticipantConnector participant, List<ModelResult<T>> results) {
            T model = getProjectModel(participant, modelType);
            addHierarchicalModel(model, results);
        }

        private void addHierarchicalModel(T model, List<ModelResult<T>> results) {
            ModelResult<T> result = createModelResult(model);
            results.add(result);

            for (HierarchicalElement child : ((HierarchicalElement) model).getChildren()) {
                addHierarchicalModel((T) child, results);
            }
        }
    }

    /**
     * Adds results using a custom model action.
     */
    private abstract class CustomActionModelResultsBuilder extends CompositeModelResultsBuilder {
        protected boolean canUseCustomModelAction(ParticipantConnector participant) {
            BuildEnvironment buildEnvironment = getProjectModel(participant, BuildEnvironment.class);
            GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
            return gradleVersion.compareTo(USE_CUSTOM_MODEL_ACTION_VERSION) >= 0;
        }

        protected void addResultsUsingModelAction(ParticipantConnector participant, List<ModelResult<T>> results) {
            ProjectConnection projectConnection = participant.connect();
            try {
                BuildActionExecuter<Map<String, T>> actionExecuter = projectConnection.action(new FetchPerProjectModelAction<T>(modelType));
                util.configureRequest(actionExecuter);
                Map<String, T> actionResults = actionExecuter.run();
                for (final String projectPath : actionResults.keySet()) {
                    ModelResult<T> result = createModelResult(actionResults.get(projectPath));
                    results.add(result);
                }
            } finally {
                projectConnection.close();
            }
        }
    }

    /**
     * Adds results using a custom model action.
     */
    private class ProjectPublicationsModelResultBuilder extends CustomActionModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return ProjectPublications.class.isAssignableFrom(modelType) && canUseCustomModelAction(participant);
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            addResultsUsingModelAction(participant, modelResults);
        }
    }

    /**
     * Adds results using a custom model action.
     */
    private class BuildInvocationsModelResultsBuilder extends CustomActionModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return BuildInvocations.class.isAssignableFrom(modelType);
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            if (canUseCustomModelAction(participant)) {
                addResultsUsingModelAction(participant, modelResults);
            } else {
                GradleProject rootProject = getProjectModel(participant, GradleProject.class);
                constructBuildInvocationsFromGradleProject(participant, rootProject, modelResults);
            }
        }

        private void constructBuildInvocationsFromGradleProject(ParticipantConnector participant, GradleProject project, List<ModelResult<T>> results) {
            Object buildInvocations = new BuildInvocationsConverter().convertSingleProject(project);
            T model = transform(participant.toProjectIdentifier(project.getPath()), buildInvocations);
            ModelResult<T> result = createModelResult(model);
            results.add(result);

            for (GradleProject childProject : project.getChildren()) {
                constructBuildInvocationsFromGradleProject(participant, childProject, results);
            }
        }

        private T transform(ProjectIdentifier projectIdentifier, Object sourceObject) {
            return protocolToModelAdapter.adapt(modelType, sourceObject, new FixedBuildIdentifierProvider(projectIdentifier));
        }
    }

    private static final class FetchPerProjectModelAction<V> implements org.gradle.tooling.BuildAction<Map<String, V>> {
        private final Class<V> modelType;
        private FetchPerProjectModelAction(Class<V> modelType) {
            this.modelType = modelType;
        }

        @Override
        public Map<String, V> execute(BuildController controller) {
            final Map<String, V> results = new HashMap<String, V>();
            fetchResults(modelType, results, controller, controller.getBuildModel().getRootProject());
            return results;
        }

        private void fetchResults(Class<V> modelType, Map<String, V> results, BuildController controller, BasicGradleProject project) {
            results.put(project.getPath(), controller.getModel(project, modelType));
            for (BasicGradleProject child : project.getChildren()) {
                fetchResults(modelType, results, controller, child);
            }
        }
    }
}
