/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;

public class ForcedDependencyMetadataWrapper extends DelegatingDependencyMetadata implements ForcingDependencyMetadata, ModuleDependencyMetadata {
    private final ModuleDependencyMetadata delegate;

    public ForcedDependencyMetadataWrapper(ModuleDependencyMetadata delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return delegate.getSelector();
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        return new ForcedDependencyMetadataWrapper(delegate.withRequestedVersion(requestedVersion));
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        return new ForcedDependencyMetadataWrapper(delegate.withReason(reason));
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        return new ForcedDependencyMetadataWrapper(delegate.withEndorseStrictVersions(endorse));
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        DependencyMetadata dependencyMetadata = delegate.withTarget(target);
        if (dependencyMetadata instanceof DefaultProjectDependencyMetadata) {
            return ((DefaultProjectDependencyMetadata) dependencyMetadata).forced();
        }
        return new ForcedDependencyMetadataWrapper((ModuleDependencyMetadata) dependencyMetadata);
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        DependencyMetadata dependencyMetadata = delegate.withTargetAndArtifacts(target, artifacts);
        if (dependencyMetadata instanceof DefaultProjectDependencyMetadata) {
            return ((DefaultProjectDependencyMetadata) dependencyMetadata).forced();
        }
        return new ForcedDependencyMetadataWrapper((ModuleDependencyMetadata) dependencyMetadata);
    }

    @Override
    public boolean isForce() {
        return true;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        return this;
    }

    public ModuleDependencyMetadata unwrap() {
        return delegate;
    }
}
