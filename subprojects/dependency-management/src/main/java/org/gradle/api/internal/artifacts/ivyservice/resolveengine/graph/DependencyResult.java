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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;

public interface DependencyResult {

    ComponentSelector getRequested();

    @Nullable
    ModuleVersionResolveException getFailure();

    /**
     * Returns the simple id of the selected component.
     */
    @Nullable
    Long getSelected();

    /**
     * Not null only when failure is not null.
     */
    @Nullable
    ComponentSelectionReason getReason();
}
