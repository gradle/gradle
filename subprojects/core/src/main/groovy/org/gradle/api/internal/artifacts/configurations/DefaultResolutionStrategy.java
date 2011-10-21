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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * by Szczepan Faber, created at: 10/7/11
 */
public class DefaultResolutionStrategy implements ResolutionStrategy {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;

    private Set<ForcedVersion> forcedVersions = new LinkedHashSet<ForcedVersion>();
    private ConflictResolution conflictResolution = new LatestConflictResolution();
    private DynamicRevisionCachePolicy dynamicRevisionCachePolicy = new FixedAgeDynamicRevisionCachePolicy(SECONDS_IN_DAY, TimeUnit.SECONDS);

    public Set<ForcedVersion> getForcedVersions() {
        return forcedVersions;
    }

    public ConflictResolution latest() {
        return new LatestConflictResolution();
    }

    public ConflictResolution strict() {
        return new StrictConflictResolution().setForcedVersions(forcedVersions);
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
        if (conflictResolution instanceof StrictConflictResolution) {
            //TODO SF - only working for strict strategy for now. Unit test.
            //I need tests for the other before I enable it. (it it make sense)
            ((StrictConflictResolution) conflictResolution).setForcedVersions(this.forcedVersions);
        }
    }

    public DynamicRevisionCachePolicy getDynamicRevisionCachePolicy() {
        return dynamicRevisionCachePolicy;
    }

    public void setDynamicRevisionCachePolicy(DynamicRevisionCachePolicy policy) {
        this.dynamicRevisionCachePolicy = policy;
    }

    public void expireDynamicRevisionsAfter(int value, TimeUnit unit) {
        this.dynamicRevisionCachePolicy = new FixedAgeDynamicRevisionCachePolicy(value, unit);
    }

    private class FixedAgeDynamicRevisionCachePolicy implements DynamicRevisionCachePolicy {
        private long expiryMillis;

        private FixedAgeDynamicRevisionCachePolicy(int value, TimeUnit units) {
            expiryMillis = TimeUnit.MILLISECONDS.convert(value, units);
        }

        public boolean canUseCachedRevision(ResolvedModule module, long ageMillis) {
            return ageMillis < expiryMillis;
        }
    }
}