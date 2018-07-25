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
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.converters.BuildInvocationsConverter;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.BuildInvocations;

public class BuildInvocationsAdapterController extends AbstractBuildController {

    private final ProtocolToModelAdapter adapter;
    private final BuildController delegate;

    public BuildInvocationsAdapterController(ProtocolToModelAdapter adapter, BuildController delegate) {
        this.adapter = adapter;
        this.delegate = delegate;
    }

    @Override
    public <T, P> T getModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException {
        if (modelType.equals(BuildInvocations.class)) {
            GradleProject gradleProject = delegate.getModel(target, GradleProject.class, parameterType, parameterInitializer);
            return adapter.adapt(modelType, new BuildInvocationsConverter().convert(gradleProject));
        }
        return delegate.getModel(target, modelType, parameterType, parameterInitializer);
    }
}
