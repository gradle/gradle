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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import org.gradle.api.artifacts.ComponentMetadata;

public class InverseVersionSelector implements VersionSelector {
    private final VersionSelector versionSelector;

    public InverseVersionSelector(VersionSelector versionSelector) {
        this.versionSelector = versionSelector;
    }

    public VersionSelector getInverseSelector() {
        return versionSelector;
    }

    @Override
    public String getSelector() {
        return "!(" + versionSelector.getSelector() + ")";
    }

    @Override
    public boolean isDynamic() {
        return versionSelector.isDynamic();
    }

    @Override
    public boolean requiresMetadata() {
        return versionSelector.requiresMetadata();
    }

    @Override
    public boolean accept(String candidate) {
        return !versionSelector.accept(candidate);
    }

    @Override
    public boolean accept(Version candidate) {
        return !versionSelector.accept(candidate);
    }

    @Override
    public boolean accept(ComponentMetadata candidate) {
        return !versionSelector.accept(candidate);
    }

    @Override
    public boolean matchesUniqueVersion() {
        return false;
    }

    @Override
    public boolean canShortCircuitWhenVersionAlreadyPreselected() {
        return false;
    }
}
