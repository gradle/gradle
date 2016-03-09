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

package org.gradle.tooling.internal.composite;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.tooling.*;
import org.gradle.tooling.composite.ModelResult;
import org.gradle.tooling.composite.ModelResults;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.consumer.BlockingResultHandler;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HasGradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class ToolingClientCompositeModelBuilder<T> implements ModelBuilder<ModelResults<T>> {
    public static final GradleVersion CUSTOM_TOOLING_ACTION_VERSION = GradleVersion.version("1.8");

    private final Class<T> modelType;
    private final Set<GradleBuildInternal> participants;
    private final List<ProgressListener> legacyProgressListeners = Lists.newArrayList();


    protected ToolingClientCompositeModelBuilder(Class<T> modelType, Set<GradleBuildInternal> participants) {
        this.modelType = modelType;
        this.participants = participants;
    }

    @Override
    public ModelResults<T> get() throws GradleConnectionException, IllegalStateException {
        BlockingResultHandler<ModelResults> handler = new BlockingResultHandler<ModelResults>(ModelResults.class);
        get(handler);
        return handler.getResult();
    }

    @Override
    public void get(final ResultHandler<? super ModelResults<T>> handler) throws IllegalStateException {
        final Set<ModelResult<T>> results = Sets.newConcurrentHashSet();

        for (GradleBuildInternal participant : participants) {
            if (hasProjectHierarchy(modelType)) {
                buildResultsFromHierarchicalModel(participant, results);
            } else {
                if (!isBuildEnvironment(modelType) && supportsCustomModelAction(participant)) {
                    // For Gradle 1.8+ : use a custom build action to retrieve the models
                    buildResultsUsingModelAction(participant, results);
                } else {
                    // Brute force: load the EclipseProject model and open a connection to each sub-project
                    // TODO:DAZ Could do something more efficient to get BuildEnvironment in newer versions
                    // (it's the same in every subproject: just need to know the paths for all subprojects
                    EclipseProject rootProject = getModel(connect(participant), EclipseProject.class);
                    buildResultsWithSeparateProjectConnections(participant, rootProject, results);
                }
            }
        }

        handler.onComplete(new ModelResults<T>() {
            @Override
            public Iterator<ModelResult<T>> iterator() {
                return results.iterator();
            }
        });
    }

    private boolean supportsCustomModelAction(GradleBuildInternal participant) {
        BuildEnvironment buildEnvironment = getModel(connect(participant), BuildEnvironment.class);
        GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
        return gradleVersion.compareTo(CUSTOM_TOOLING_ACTION_VERSION) >= 0;
    }

    private boolean isBuildEnvironment(Class<T> modelType) {
        return BuildEnvironment.class.isAssignableFrom(modelType);
    }

    private boolean hasProjectHierarchy(Class<T> modelType) {
        return HierarchicalElement.class.isAssignableFrom(modelType)
            && (GradleProject.class.isAssignableFrom(modelType) || HasGradleProject.class.isAssignableFrom(modelType));
    }

    private void buildResultsFromHierarchicalModel(GradleBuildInternal participant, Set<ModelResult<T>> results) {
        Class<T> modelType = this.modelType;
        T model = getModel(connect(participant), modelType);
        addHierarchicalModel(model, participant, results);
    }

    private void addHierarchicalModel(T model, GradleBuildInternal participant, Set<ModelResult<T>> results) {
        String projectPath = getGradleProject(model).getPath();
        ModelResult<T> result = new DefaultModelResult<T>(model, participant.toProjectIdentity(projectPath));
        results.add(result);

        System.out.println("Added " + model + " at project path " + projectPath);

        for (HierarchicalElement child : ((HierarchicalElement) model).getChildren()) {
            addHierarchicalModel((T) child, participant, results);
        }
    }

    private GradleProject getGradleProject(T model) {
        assert hasProjectHierarchy(modelType);
        if (GradleProject.class.isAssignableFrom(modelType)) {
            return (GradleProject) model;
        }
        return ((HasGradleProject) model).getGradleProject();
    }

    private void buildResultsUsingModelAction(GradleBuildInternal participant, Set<ModelResult<T>> results) {
        ProjectConnection projectConnection = connect(participant);

        try {
            BuildActionExecuter<Map<String, T>> actionExecuter = projectConnection.action(new FetchPerProjectModelAction<T>(modelType));
            configureRequest(actionExecuter);
            Map<String, T> actionResults = actionExecuter.run();
            for (String projectPath : actionResults.keySet()) {
                ModelResult<T> result = new DefaultModelResult<T>(actionResults.get(projectPath), participant.toProjectIdentity(projectPath));
                results.add(result);
            }
        } finally {
            projectConnection.close();
        }
    }

    private void buildResultsWithSeparateProjectConnections(GradleBuildInternal participant, EclipseProject project, Set<ModelResult<T>> results) {
        ProjectConnection connection = new GradleParticipantBuild(participant, project.getProjectDirectory()).connect();
        T model = getModel(connection, modelType);
        ModelResult<T> result = new DefaultModelResult<T>(model, participant.toProjectIdentity(project.getGradleProject().getPath()));
        results.add(result);

        for (EclipseProject gradleProject : project.getChildren()) {
            buildResultsWithSeparateProjectConnections(participant, gradleProject, results);
        }
    }

    private <V> V getModel(ProjectConnection connection, Class<V> modelType) {
        try {
            ModelBuilder<V> modelBuilder = connection.model(modelType);
            configureRequest(modelBuilder);
            return modelBuilder.get();
        } finally {
            connection.close();
        }
    }

    private <V extends ConfigurableLauncher> void configureRequest(ConfigurableLauncher<V> request) {
        for (ProgressListener progressListener : legacyProgressListeners) {
            request.addProgressListener(progressListener);
        }
    }

    private ProjectConnection connect(GradleBuildInternal build) {
        return new GradleParticipantBuild(build).connect();
    }

    // TODO: Make all configuration methods configure underlying model builders
    private ToolingClientCompositeModelBuilder<T> unsupportedMethod() {
        throw new UnsupportedMethodException("Not supported for composite connections.");
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> addProgressListener(ProgressListener listener) {
        legacyProgressListeners.add(listener);
        return this;
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        return addProgressListener(listener, Sets.newHashSet(operationTypes));
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<ModelResults<T>> withCancellationToken(CancellationToken cancellationToken) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> forTasks(String... tasks) {
        return forTasks(Lists.newArrayList(tasks));
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> forTasks(Iterable<String> tasks) {
        return unsupportedMethod();
    }


    @Override
    public ToolingClientCompositeModelBuilder<T> withArguments(String... arguments) {
        return withArguments(Lists.newArrayList(arguments));
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> withArguments(Iterable<String> arguments) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setStandardOutput(OutputStream outputStream) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setStandardError(OutputStream outputStream) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setColorOutput(boolean colorOutput) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setStandardInput(InputStream inputStream) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setJavaHome(File javaHome) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setJvmArguments(String... jvmArguments) {
        return unsupportedMethod();
    }

    @Override
    public ToolingClientCompositeModelBuilder<T> setJvmArguments(Iterable<String> jvmArguments) {
        return unsupportedMethod();
    }


    private static final class FetchPerProjectModelAction<V> implements org.gradle.tooling.BuildAction<Map<String, V>> {
        private final Class<V> modelType;
        private FetchPerProjectModelAction(Class<V> modelType) {
            this.modelType = modelType;
        }

        @Override
        public Map<String, V> execute(BuildController controller) {
            final Map<String, V> results = new HashMap<String, V>();
            fetchResults(modelType, results, controller, controller.getBuildModel().getRootProject(), controller.getBuildModel().getRootProject());
            return results;
        }

        private void fetchResults(Class<V> modelType, Map<String, V> results, BuildController controller, BasicGradleProject project, BasicGradleProject rootProject) {
            results.put(project.getPath(), controller.getModel(project, modelType));
            for (BasicGradleProject child : project.getChildren()) {
                fetchResults(modelType, results, controller, child, rootProject);
            }
        }
    }
}
