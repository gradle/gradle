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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Action;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.ResilientResult;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.internal.adapter.ObjectGraphAdapter;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.internal.Exceptions;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class UnparameterizedBuildController extends HasCompatibilityMapping implements BuildController {
    private final ProtocolToModelAdapter adapter;
    private final ObjectGraphAdapter resultAdapter;
    private final ModelMapping modelMapping;
    protected final VersionDetails gradleVersion;
    private final File rootDir;

    public UnparameterizedBuildController(ProtocolToModelAdapter adapter, ModelMapping modelMapping, VersionDetails gradleVersion, File rootDir) {
        this.adapter = adapter;
        // Treat all models returned to the action as part of the same object graph
        this.resultAdapter = adapter.newGraph();
        this.modelMapping = modelMapping;
        this.gradleVersion = gradleVersion;
        this.rootDir = rootDir;
    }

    @Override
    public <T> T getModel(Class<T> modelType) throws UnknownModelException {
        return getModel(null, modelType);
    }

    @Override
    public <T> ResilientResult<T> getResilientModel(Class<T> modelType) throws UnknownModelException {
        return getResilientModel(null, modelType);
    }

    @Override
    public <T> T findModel(Class<T> modelType) {
        return findModel(null, modelType);
    }

    @Override
    public GradleBuild getBuildModel() {
        return getModel(null, GradleBuild.class);
    }

    @Override
    public <T> T getModel(Model target, Class<T> modelType) throws UnknownModelException {
        return getModel(target, modelType, null, null);
    }

    @Override
    public <T> ResilientResult<T> getResilientModel(Model target, Class<T> modelType) throws UnknownModelException {
        return getResilientModel(target, modelType, null, null);
    }

    @Override
    public <T> T findModel(Model target, Class<T> modelType) {
        return findModel(target, modelType, null, null);
    }

    @Override
    public <T, P> T getModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException {
        return getModel(null, modelType, parameterType, parameterInitializer);
    }

    @Override
    public <T, P> ResilientResult<T> getResilientModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnknownModelException {
        return getResilientModel(null, modelType, parameterType, parameterInitializer);
    }

    @Override
    public <T, P> T findModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) {
        return findModel(null, modelType, parameterType, parameterInitializer);
    }

    @Override
    public <T, P> T findModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) {
        try {
            return getModel(target, modelType, parameterType, parameterInitializer);
        } catch (UnknownModelException e) {
            // Ignore
            return null;
        }
    }

    @Override
    public <T, P> T getModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException, UnknownModelException {
        BuildResult<?> result;
        try {
            result = getModel(
                getOriginalTarget(target),
                getModelIdentifier(modelType),
                initializeParameter(parameterType, parameterInitializer)
            );
        } catch (InternalUnsupportedModelException e) {
            throw Exceptions.unknownModel(modelType, e);
        }

        return buildView(target, modelType, result.getModel());
    }

    @Override
    public <T, P> ResilientResult<T> getResilientModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException, UnknownModelException {
        BuildResult<ResilientResult<?>> result;
        try {
            result = getResilientModel(
                getOriginalTarget(target),
                getModelIdentifier(modelType),
                initializeParameter(parameterType, parameterInitializer)
            );
        } catch (InternalUnsupportedModelException e) {
            throw Exceptions.unknownModel(modelType, e);
        }

        return buildResilientView(target, modelType, result.getModel());
    }

    private <T> @javax.annotation.Nullable ResilientResult<T> buildResilientView(Model target, Class<T> modelType, Object model) {
        TypelessResilientResult resilientResultView = resultAdapter.builder(TypelessResilientResult.class).build(model);

        Object obj = buildView(target, modelType, resilientResultView.getModel());
        @SuppressWarnings({"unchecked"}) T modelView = (T) obj;

        ResilientResult<T> resilientResult = new ResilientResult<T>() {
            @Override
            public boolean hasFailures() {
                return resilientResultView.hasFailures();
            }

            @Override
            public T getModel() {
                return modelView;
            }

            @Override
            public List<Problem> getProblems() {
                return resilientResultView.getProblems();
            }
        };
        return resilientResult; // TODO: this whole method is a bit of a hack...
    }

    private <T> @javax.annotation.Nullable T buildView(Model target, Class<T> modelType, Object model) {
        ViewBuilder<T> viewBuilder = resultAdapter.builder(modelType);
        applyCompatibilityMapping(viewBuilder, new DefaultProjectIdentifier(rootDir, getProjectPath(target)));
        return viewBuilder.build(model);
    }

    private <T> ModelIdentifier getModelIdentifier(Class<T> modelType) {
        return modelMapping.getModelIdentifierFromModelType(modelType);
    }

    @Nonnull
    private Object getOriginalTarget(Model target) {
        return target == null ? null : adapter.unpack(target);
    }

    private <P> P initializeParameter(Class<P> parameterType, Action<? super P> parameterInitializer) {
        validateParameters(parameterType, parameterInitializer);
        if (parameterType != null) {
            // TODO: move this to ObjectFactory
            P parameter = parameterType.cast(Proxy.newProxyInstance(parameterType.getClassLoader(), new Class<?>[]{parameterType}, new ToolingParameterProxy()));
            parameterInitializer.execute(parameter);
            return parameter;
        } else {
            return null;
        }
    }

    private <P> void validateParameters(Class<P> parameterType, Action<? super P> parameterInitializer) {
        if ((parameterType == null && parameterInitializer != null) || (parameterType != null && parameterInitializer == null)) {
            throw new NullPointerException("parameterType and parameterInitializer both need to be set for a parameterized model request.");
        }

        if (parameterType != null) {
            ToolingParameterProxy.validateParameter(parameterType);
        }
    }

    private String getProjectPath(Model target) {
        if (target instanceof ProjectModel) {
            return ((ProjectModel) target).getProjectIdentifier().getProjectPath();
        } else {
            return ":";
        }
    }

    protected abstract BuildResult<?> getModel(@Nullable Object target, ModelIdentifier modelIdentifier, @Nullable Object parameter) throws InternalUnsupportedModelException;

    protected abstract BuildResult<ResilientResult<?>> getResilientModel(@Nullable Object target, ModelIdentifier modelIdentifier, @Nullable Object parameter) throws InternalUnsupportedModelException;

    @Override
    public boolean getCanQueryProjectModelInParallel(Class<?> modelType) {
        return false;
    }

    @Override
    public <T> List<T> run(Collection<? extends BuildAction<? extends T>> actions) {
        List<T> results = new ArrayList<T>(actions.size());
        List<Throwable> failures = new ArrayList<Throwable>();
        for (BuildAction<? extends T> action : actions) {
            try {
                T result = action.execute(this);
                results.add(result);
            } catch (Throwable t) {
                failures.add(t);
            }
        }
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(failures, null);
        }
        return results;
    }

    @Override
    public void send(Object value) {
        throw new UnsupportedVersionException(String.format("Gradle version %s does not support streaming values to the client.", gradleVersion.getVersion()));
    }

    public interface TypelessResilientResult {

        boolean hasFailures();

        Object getModel();

        List<Problem> getProblems();

    }
}
