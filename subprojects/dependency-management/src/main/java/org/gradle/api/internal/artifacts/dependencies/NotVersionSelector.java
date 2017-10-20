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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

class NotVersionSelector implements VersionSelector {
    private final VersionSelector delegate;

    public NotVersionSelector(VersionSelector delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isDynamic() {
        return delegate.isDynamic();
    }

    @Override
    public boolean requiresMetadata() {
        return delegate.requiresMetadata();
    }

    @Override
    public boolean matchesUniqueVersion() {
        return delegate.matchesUniqueVersion();
    }

    @Override
    public boolean accept(String candidate) {
        return !delegate.accept(candidate);
    }

    @Override
    public boolean accept(Version candidate) {
        return !delegate.accept(candidate);
    }

    @Override
    public boolean accept(ComponentMetadata candidate) {
        return !delegate.accept(candidate);
    }

    @Override
    public boolean canShortCircuitWhenVersionAlreadyPreselected() {
        return delegate.canShortCircuitWhenVersionAlreadyPreselected();
    }
}
