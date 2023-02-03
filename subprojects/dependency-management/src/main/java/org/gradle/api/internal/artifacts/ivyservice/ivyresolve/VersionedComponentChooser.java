/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.RejectedByRuleVersion;
import org.gradle.internal.resolve.result.ComponentSelectionContext;

import javax.annotation.Nullable;
import java.util.Collection;

public interface VersionedComponentChooser {
    @Nullable
    ComponentResolveMetadata selectNewestComponent(ComponentResolveMetadata one, ComponentResolveMetadata two);

    void selectNewestMatchingComponent(Collection<? extends ModuleComponentResolveState> versions, ComponentSelectionContext result, VersionSelector versionSelector, VersionSelector rejectedVersionSelector, ImmutableAttributes consumerAttributes);

    @Nullable
    RejectedByRuleVersion isRejectedComponent(ModuleComponentIdentifier candidateIdentifier, MetadataProvider metadataProvider);
}
