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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;

import java.util.List;

/**
 * A {@link ResolvedGraphComponent} implementation that is detached from the original resolution process.
 * Instances are created when de-serializing the resolution result.
 */
public class DetachedComponentResult implements ResolvedGraphComponent {
    private final Long resultId;
    private final ModuleVersionIdentifier id;
    private final ComponentSelectionReason reason;
    private final ComponentIdentifier componentIdentifier;
    private final List<ResolvedVariantResult> resolvedVariants;
    private final String repositoryName;

    public DetachedComponentResult(Long resultId, ModuleVersionIdentifier id, ComponentSelectionReason reason, ComponentIdentifier componentIdentifier, List<ResolvedVariantResult> resolvedVariants, String repositoryName) {
        this.resultId = resultId;
        this.id = id;
        this.reason = reason;
        this.componentIdentifier = componentIdentifier;
        this.resolvedVariants = resolvedVariants;
        this.repositoryName = repositoryName;
    }

    @Override
    public Long getResultId() {
        return resultId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersion() {
        return id;
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return reason;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public String getRepositoryName() {
        return repositoryName;
    }

    @Override
    public List<ResolvedVariantResult> getResolvedVariants() {
        return resolvedVariants;
    }
}
