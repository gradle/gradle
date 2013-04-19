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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

/**
 * The result of attempting to resolve a dependency descriptor to the meta-data for a module version.
 *
 * TODO - detach this from ModuleVersionMetaData, so that it has-a instead of is-a.
 */
public interface BuildableModuleVersionMetaData extends ModuleVersionMetaData {
    static enum State {
        Resolved, Missing, Failed, ProbablyMissing, Unknown
    }

    /**
     * Returns the current state of this descriptor.
     */
    State getState();

    @Nullable
    ModuleVersionResolveException getFailure();

    /**
     * Marks the module version as resolved, with the given meta-data.
     */
    void resolved(ModuleDescriptor descriptor, boolean changing, ModuleSource moduleSource);

    /**
     * Marks the module version as resolved, with the given meta-data.
     */
    void resolved(ModuleVersionIdentifier id, ModuleDescriptor descriptor, boolean changing, ModuleSource moduleSource);

    /**
     * Marks the resolve as failed with the given exception.
     */
    void failed(ModuleVersionResolveException failure);

    /**
     * Marks the module version as definitely missing.
     */
    void missing();

    /**
     * Marks the module version as probably missing.
     */
    void probablyMissing();

    /**
     * The repository-specific source for this module version.
     */
    public ModuleSource getModuleSource();

    void setModuleSource(ModuleSource moduleSource);

    /**
     * Replaces the dependencies of this module version.
     */
    void setDependencies(Iterable<? extends DependencyMetaData> dependencies);
}
