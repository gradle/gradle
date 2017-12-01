/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;

import java.util.List;

public abstract class AbstractDependencyMetadataAdapter<T extends DependencyMetadata> implements DependencyMetadata<T> {
    private final List<ModuleDependencyMetadata> container;
    private final int originalIndex;

    public AbstractDependencyMetadataAdapter(List<ModuleDependencyMetadata> container, int originalIndex) {
        this.container = container;
        this.originalIndex = originalIndex;
    }

    private ModuleDependencyMetadata getOriginalMetadata() {
        return container.get(originalIndex);
    }

    private void updateMetadata(ModuleDependencyMetadata modifiedMetadata) {
        container.set(originalIndex, modifiedMetadata);
    }

    @Override
    public String getGroup() {
        return getOriginalMetadata().getSelector().getGroup();
    }

    @Override
    public String getName() {
        return getOriginalMetadata().getSelector().getModule();
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return getOriginalMetadata().getSelector().getVersionConstraint();
    }

    @Override
    public T version(Action<? super MutableVersionConstraint> configureAction) {
        DefaultMutableVersionConstraint mutableVersionConstraint = new DefaultMutableVersionConstraint(getVersionConstraint());
        configureAction.execute(mutableVersionConstraint);
        ModuleDependencyMetadata dependencyMetadata = getOriginalMetadata().withRequestedVersion(mutableVersionConstraint);
        updateMetadata(dependencyMetadata);
        return Cast.uncheckedCast(this);
    }

    @Override
    public String toString() {
        return getGroup() + ":" + getName() + ":" + getVersionConstraint();
    }
}
