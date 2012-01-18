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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;

/**
 * Command methods for controlling dependency resolution via the DSL.
 */
public interface DependencyResolutionControl extends ResolutionControl {
    /**
     * Returns the requested module version selector, which may be a dynamic version
     */
    ModuleVersionSelector getRequest();

    // TODO:DAZ This should really be a set of modules, so we can later do version ranges
    /**
     * Provides the cached result of resolving the module selector.
     * @return the cached result or null if the dependency is not cached
     */
    ModuleVersionIdentifier getCachedResult();
}
