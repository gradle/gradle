/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.List;

public class DefaultProjectDependencyMetadata extends DelegatingDependencyMetadata implements ForcingDependencyMetadata {
    private final ProjectComponentSelector selector;
    private final DependencyMetadata delegate;

    public DefaultProjectDependencyMetadata(ProjectComponentSelector selector, DependencyMetadata delegate) {
        super(delegate);
        this.selector = selector;
        this.delegate = delegate;
    }

    @Override
    public ProjectComponentSelector getSelector() {
        return selector;
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return Collections.emptyList();
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target.equals(selector)) {
            return this;
        }
        return delegate.withTarget(target);
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (target.equals(selector) && delegate.getArtifacts().equals(artifacts)) {
            return this;
        }
        return delegate.withTargetAndArtifacts(target, artifacts);
    }

    @Override
    public boolean isForce() {
        if (delegate instanceof ForcingDependencyMetadata) {
            return ((ForcingDependencyMetadata) delegate).isForce();
        }
        return false;
    }

    @Override
    public ForcingDependencyMetadata forced() {
        if (delegate instanceof ForcingDependencyMetadata) {
            return ((ForcingDependencyMetadata) delegate).forced();
        }
        return this;
    }
}
