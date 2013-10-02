/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.List;

/**
 * The meta-data for a module version required during dependency resolution.
 */
public interface ModuleVersionMetaData {
    ModuleVersionIdentifier getId();

    /**
     * Returns this module version as an Ivy ModuleDescriptor. This method is here to allow us to migrate away from the Ivy types
     * and will be removed.
     */
    ModuleDescriptor getDescriptor();

    List<DependencyMetaData> getDependencies();

    @Nullable
    ConfigurationMetaData getConfiguration(String name);

    boolean isChanging();

    boolean isMetaDataOnly();

    String getStatus();

    List<String> getStatusScheme();
}
