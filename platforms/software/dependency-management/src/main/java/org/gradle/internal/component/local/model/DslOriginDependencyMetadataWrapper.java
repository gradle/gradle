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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.List;

/**
 * Wraps a local dependency metadata along with the DSL {@link Dependency} that created it.
 * <p>
 * This type is going away in 9.0. All features that leverage the original dependency have been deprecated.
 * <p>
 * When serializing this type to the configuration cache, we serialize the delegate instead of this type.
 */
public class DslOriginDependencyMetadataWrapper extends DelegatingDependencyMetadata implements DslOriginDependencyMetadata, LocalOriginDependencyMetadata {
    private final LocalOriginDependencyMetadata delegate;
    private final Dependency source;

    public DslOriginDependencyMetadataWrapper(LocalOriginDependencyMetadata delegate, Dependency source) {
        super(delegate);
        this.delegate = delegate;
        this.source = source;
    }

    /**
     * Exposed for CC serialization.
     */
    public LocalOriginDependencyMetadata getDelegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Dependency getSource() {
        return source;
    }

    @Override
    public boolean isForce() {
        return delegate.isForce();
    }

    @Override
    public boolean isFromLock() {
        return delegate.isFromLock();
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public LocalOriginDependencyMetadata withTarget(ComponentSelector target) {
        return new DslOriginDependencyMetadataWrapper(delegate.withTarget(target), source);
    }

    @Override
    public LocalOriginDependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        return new DslOriginDependencyMetadataWrapper(delegate.withTargetAndArtifacts(target, artifacts), source);
    }

    @Override
    public LocalOriginDependencyMetadata forced() {
        return new DslOriginDependencyMetadataWrapper(delegate.forced(), source);
    }
}
