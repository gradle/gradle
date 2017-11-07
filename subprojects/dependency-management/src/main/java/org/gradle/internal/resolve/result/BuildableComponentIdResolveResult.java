/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public interface BuildableComponentIdResolveResult extends ComponentIdResolveResult, ResourceAwareResolveResult {
    void resolved(ComponentIdentifier id, ModuleVersionIdentifier moduleVersionIdentifier);

    void resolved(ComponentResolveMetadata metaData);

    void setSelectionReason(ComponentSelectionReason reason);

    void failed(ModuleVersionResolveException failure);

    ResolvedVersionConstraint getResolvedVersionConstraint();

    void setResolvedVersionConstraint(ResolvedVersionConstraint versionConstraint);
}
