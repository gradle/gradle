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

package org.gradle.tooling.internal.consumer.versioning;

import org.gradle.api.Nullable;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.Model;

/**
 * by Szczepan Faber, created at: 1/13/12
 */
public abstract class VersionDetails {
    private final String providerVersion;

    public VersionDetails(String version) {
        providerVersion = version;
    }

    public String getVersion() {
        return providerVersion;
    }

    /**
     * Returns true if this provider may support the given model type. Returns false if it is known that the
     * provider does not support the given model type and <em>should not</em> be asked to provide it.
     */
    public boolean isModelSupported(Class<?> modelType) {
        return false;
    }

    public boolean supportsConfiguringJavaHome() {
        return false;
    }

    public boolean supportsConfiguringJvmArguments() {
        return false;
    }

    public boolean supportsConfiguringStandardInput() {
        return false;
    }

    public boolean supportsRunningTasksWhenBuildingModel() {
        return false;
    }

    public boolean supportsGradleProjectModel() {
        return false;
    }

    /**
     * Maps the given model type to a protocol type, for those older provider versions that use protocol types.
     *
     * @return null for an unknown model type.
     */
    @Nullable
    public Class<?> mapModelTypeToProtocolType(Class<?> modelType) {
        return modelType == Void.class ? modelType : new ModelMapping().getProtocolType(modelType.asSubclass(Model.class));
    }

    /**
     * Maps the given model type to a cross-version identifier for the model.
     */
    abstract public ModelIdentifier mapModelTypeToModelIdentifier(Class<?> modelType);
}