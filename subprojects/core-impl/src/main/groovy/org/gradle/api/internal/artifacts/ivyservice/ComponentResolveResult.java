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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;

public interface ComponentResolveResult {
    /**
     * Returns the module version id of the component.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    ModuleVersionIdentifier getId() throws ModuleVersionResolveException;

    /**
     * Returns the meta-data for the component.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    ComponentMetaData getMetaData() throws ModuleVersionResolveException;

    /**
     * Returns the resolve failure, if any.
     */
    @Nullable
    ModuleVersionResolveException getFailure();
}
