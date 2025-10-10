/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalFetchModelResult;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.protocol.resiliency.InternalFetchAwareBuildController;
import org.gradle.tooling.model.Model;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

@NullMarked
class FetchAwareBuildControllerAdapter extends StreamingAwareBuildControllerAdapter {
    private final InternalFetchAwareBuildController fetch;

    public FetchAwareBuildControllerAdapter(InternalBuildControllerVersion2 buildController, ProtocolToModelAdapter adapter, ModelMapping modelMapping, VersionDetails gradleVersion, File rootDir) {
        super(buildController, adapter, modelMapping, gradleVersion, rootDir);
        fetch = (InternalFetchAwareBuildController) buildController;
    }

    @Override
    public <T extends Model, M, P> FetchModelResult<T, M> fetch(
        @Nullable T target,
        Class<M> modelType,
        @Nullable Class<P> parameterType,
        @Nullable Action<? super P> parameterInitializer
    ) {
        Object originalTarget = unpackModelTarget(target);
        ModelIdentifier modelIdentifier = getModelIdentifierFromModelType(modelType);
        P parameter = parameterInitializer != null ? initializeParameter(parameterType, parameterInitializer) : null;
        InternalFetchModelResult<Object> result = fetch.fetch(originalTarget, modelIdentifier, parameter);
        return adaptResult(target, modelType, result);
    }

    private <T extends Model, M> FetchModelResult<T, M> adaptResult(@Nullable T target, Class<M> modelType, InternalFetchModelResult<Object> result) {
        Object model = result.getModel();
        M adaptedModel = model != null ? adaptModel(target, modelType, model) : null;
        return new FetchModelResult<T, M>() {
            @Nullable
            List<Failure> failures = null;

            @Override
            public T getTarget() {
                return target;
            }

            @Override
            public M getModel() {
                return adaptedModel;
            }

            @Override
            public Collection<? extends Failure> getFailures() {
                if (failures == null) {
                    failures = BuildProgressListenerAdapter.toFailures(result.getFailures());
                }
                return failures;
            }
        };
    }
}
