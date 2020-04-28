/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ObjectGraphAdapter;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.internal.Exceptions;

import java.io.File;
import java.lang.reflect.Proxy;

class BuildControllerAdapter extends AbstractBuildController implements BuildController {
    private final InternalBuildControllerAdapter buildController;
    private final ProtocolToModelAdapter adapter;
    private final ObjectGraphAdapter resultAdapter;
    private final ModelMapping modelMapping;
    private final File rootDir;

    public BuildControllerAdapter(ProtocolToModelAdapter adapter, InternalBuildControllerAdapter buildController, ModelMapping modelMapping, File rootDir) {
        this.adapter = adapter;
        this.buildController = buildController;
        this.modelMapping = modelMapping;
        this.rootDir = rootDir;
        // Treat all models returned to the action as part of the same object graph
        resultAdapter = adapter.newGraph();
    }

    @Override
    public <T, P> T getModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException {
        ModelIdentifier modelIdentifier = modelMapping.getModelIdentifierFromModelType(modelType);
        Object originalTarget = target == null ? null : adapter.unpack(target);

        P parameter = initializeParameter(parameterType, parameterInitializer);

        BuildResult<?> result;
        try {
            result = buildController.getModel(originalTarget, modelIdentifier, parameter);
        } catch (InternalUnsupportedModelException e) {
            throw Exceptions.unknownModel(modelType, e);
        }

        ViewBuilder<T> viewBuilder = resultAdapter.builder(modelType);
        applyCompatibilityMapping(viewBuilder, new DefaultProjectIdentifier(rootDir, getProjectPath(target)));
        return viewBuilder.build(result.getModel());
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
}
