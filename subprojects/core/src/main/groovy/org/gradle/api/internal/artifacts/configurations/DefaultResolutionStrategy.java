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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.configurations.conflicts.LatestConflictResolution;
import org.gradle.api.internal.artifacts.configurations.conflicts.StrictConflictResolution;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.DefaultDynamicVersionCachePolicy;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.DynamicVersionCachePolicy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * by Szczepan Faber, created at: 10/7/11
 */
public class DefaultResolutionStrategy implements ResolutionStrategyInternal {

    private Set<ForcedVersion> forcedVersions = new LinkedHashSet<ForcedVersion>();
    private ConflictResolution conflictResolution = new LatestConflictResolution();
    private final DefaultDynamicVersionCachePolicy dynamicRevisionCachePolicy = new DefaultDynamicVersionCachePolicy();

    public Set<ForcedVersion> getForcedVersions() {
        return forcedVersions;
    }

    public ConflictResolution latest() {
        return new LatestConflictResolution();
    }

    public ConflictResolution strict() {
        return new StrictConflictResolution();
    }

    public ConflictResolution getConflictResolution() {
        return this.conflictResolution;
    }

    public void setConflictResolution(ConflictResolution conflictResolution) {
        assert conflictResolution != null : "Cannot set null conflictResolution";
        this.conflictResolution = conflictResolution;
    }

    public void force(String... forcedVersions) {
        assert forcedVersions != null : "forcedVersions cannot be null";
        for (String forcedVersion : forcedVersions) {
            this.forcedVersions.add(new DefaultForcedVersion(forcedVersion));
        }
    }

    public DynamicVersionCachePolicy getDynamicRevisionCachePolicy() {
        return dynamicRevisionCachePolicy;
    }

    public void expireDynamicVersionsAfter(int value, TimeUnit unit) {
        this.dynamicRevisionCachePolicy.expireDynamicRevisionsAfter(value, unit);
    }
}