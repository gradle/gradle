/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultArtifactSelectionDetails implements ArtifactSelectionDetailsInternal {

    private final DefaultDependencySubstitution owner;
    private final List<ArtifactVariantSelector> requestedSelectors;
    private List<ArtifactVariantSelector> targetSelectors;

    public DefaultArtifactSelectionDetails(DefaultDependencySubstitution defaultDependencySubstitution, List<IvyArtifactName> requested) {
        this.owner = defaultDependencySubstitution;
        this.requestedSelectors = requested.isEmpty() ? ImmutableList.of():ImmutableList.copyOf(requested.stream()
            .map(e -> new DefaultArtifactVariantSelector(e.getType(), e.getExtension(), e.getClassifier()))
            .collect(Collectors.toList()));
    }

    @Override
    public boolean hasSelectors() {
        return !requestedSelectors.isEmpty();
    }

    @Override
    public List<ArtifactVariantSelector> getRequestedSelectors() {
        return requestedSelectors;
    }

    @Override
    public void selectArtifact(String type, @Nullable String extension, @Nullable String classifier) {
        selectArtifact(new DefaultArtifactVariantSelector(type, extension, classifier));
    }

    @Override
    public void selectArtifact(ArtifactVariantSelector selector) {
        if (targetSelectors == null) {
            targetSelectors = Lists.newArrayList();
            owner.addRuleDescriptor(ComponentSelectionReasons.SELECTED_BY_RULE);
        }
        targetSelectors.add(selector);
    }

    @Override
    public boolean isUpdated() {
        return targetSelectors != null;
    }

    @Override
    public List<ArtifactVariantSelector> getTargetSelectors() {
        return targetSelectors == null ? Collections.emptyList() : ImmutableList.copyOf(targetSelectors);
    }
}
