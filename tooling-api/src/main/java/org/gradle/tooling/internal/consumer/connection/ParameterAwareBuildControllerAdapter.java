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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;

import javax.annotation.Nullable;
import java.io.File;

class ParameterAwareBuildControllerAdapter extends UnparameterizedBuildController {
    private final InternalBuildControllerVersion2 buildController;

    public ParameterAwareBuildControllerAdapter(InternalBuildControllerVersion2 buildController, ProtocolToModelAdapter adapter, ModelMapping modelMapping, File rootDir) {
        super(adapter, modelMapping, rootDir);
        this.buildController = buildController;
    }

    @Override
    protected BuildResult<?> getModel(@Nullable Object target, ModelIdentifier modelIdentifier, @Nullable Object parameter) throws InternalUnsupportedModelException {
        return buildController.getModel(target, modelIdentifier, parameter);
    }
}
