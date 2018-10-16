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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StringVersioned;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.RejectedBySelectorVersion;
import org.gradle.internal.resolve.RejectedVersion;

import javax.annotation.Nullable;
import java.util.Collection;

public interface ComponentResolutionState extends StringVersioned {
    ComponentIdentifier getComponentId();

    ModuleVersionIdentifier getId();

    void selectedBy(ResolvableSelectorState selectorState);

    /**
     * Returns the meta-data for the component. Resolves if not already resolved.
     *
     * @return null if the meta-data is not available due to some failure.
     */
    @Nullable
    ComponentResolveMetadata getMetadata();

    void addCause(ComponentSelectionDescriptorInternal componentSelectionDescription);

    void reject();

    boolean isRejected();

    void unmatched(Collection<RejectedBySelectorVersion> unmatchedVersions);

    void rejected(Collection<RejectedVersion> rejectedVersions);
}
