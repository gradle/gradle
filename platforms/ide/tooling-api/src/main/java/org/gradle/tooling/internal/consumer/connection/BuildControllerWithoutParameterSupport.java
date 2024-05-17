/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.Model;

import javax.annotation.Nullable;
import java.io.File;

@SuppressWarnings("deprecation")
public class BuildControllerWithoutParameterSupport extends UnparameterizedBuildController {
    private final org.gradle.tooling.internal.protocol.InternalBuildController buildController;
    private final VersionDetails gradleVersion;

    public BuildControllerWithoutParameterSupport(org.gradle.tooling.internal.protocol.InternalBuildController buildController, ProtocolToModelAdapter adapter, ModelMapping modelMapping, File rootDir, VersionDetails gradleVersion) {
        super(adapter, modelMapping, rootDir);
        this.buildController = buildController;
        this.gradleVersion = gradleVersion;
    }

    @Override
    public <T, P> T getModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException {
        if (parameterType != null) {
            throw new UnsupportedVersionException(String.format("Gradle version %s does not support parameterized tooling models.", gradleVersion.getVersion()));
        }
        return super.getModel(target, modelType, parameterType, parameterInitializer);
    }

    @Override
    protected BuildResult<?> getModel(@Nullable Object target, ModelIdentifier modelIdentifier, @Nullable Object parameter) throws InternalUnsupportedModelException {
        return buildController.getModel(target, modelIdentifier);
    }
}
