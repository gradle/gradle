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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public interface BuildableComponentResolveResult extends ComponentResolveResult, ResourceAwareResolveResult {
    /**
     * Marks the component as resolved, with the given meta-data.
     */
    void resolved(ComponentResolveMetaData metaData);

    /**
     * Marks the resolve as failed with the given exception.
     */
    BuildableComponentResolveResult failed(ModuleVersionResolveException failure);

    /**
     * Marks the component as not found.
     */
    void notFound(ModuleVersionSelector versionSelector);

    /**
     * Marks the component as not found.
     */
    void notFound(ModuleVersionIdentifier versionIdentifier);

    /**
     * Replaces the meta-data in the result. Result must already be resolved.
     */
    void setMetaData(ComponentResolveMetaData metaData);
}
