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
package org.gradle.api.artifacts.cache;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;

/**
 * Command methods for controlling module resolution via the DSL.
 */
@Incubating
public interface ModuleResolutionControl extends ResolutionControl<ModuleVersionIdentifier, ResolvedModuleVersion> {
    // TODO: This should be part of the cached result?
    /**
     * Does the module change content over time?
     * @return if the module is changing
     */
    boolean isChanging();
}
