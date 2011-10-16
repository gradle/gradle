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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.conflict.AbstractConflictManager;
import org.apache.ivy.plugins.conflict.LatestConflictManager;
import org.apache.ivy.plugins.conflict.StrictConflictException;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ForcedVersion;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.configurations.conflicts.DependencySelector;
import org.gradle.api.internal.artifacts.configurations.conflicts.LatestConflictResolution;
import org.gradle.api.internal.artifacts.configurations.conflicts.StrictConflictResolution;

import java.util.Set;

/**
 * Contains ivy settings and conflict management. The purpose of this class is to insulate from ivy a bit.
 *
 * @author: Szczepan Faber, created at: 9/29/11
 */
public class IvyConfig {

    private final IvySettings ivySettings;
    private final ResolutionStrategy resolutionStrategy;

    public IvyConfig(IvySettings ivySettings, ResolutionStrategy resolutionStrategy) {
        assert ivySettings != null : "ivySettings cannot be null!";
        assert resolutionStrategy != null : "resolutionStrategy cannot be null!";
        this.ivySettings = ivySettings;
        this.resolutionStrategy = resolutionStrategy;
    }

    public void applyConflictManager(DefaultModuleDescriptor moduleDescriptor) {
        AbstractConflictManager conflictManager = createIvyConflictManager();
        conflictManager.setSettings(ivySettings);
        moduleDescriptor.addConflictManager(new ModuleId(ExactPatternMatcher.ANY_EXPRESSION,
                ExactPatternMatcher.ANY_EXPRESSION), ExactPatternMatcher.INSTANCE,
                conflictManager);
    }

    private AbstractConflictManager createIvyConflictManager() {
        if (resolutionStrategy.getConflictResolution()  instanceof LatestConflictResolution) {
            return new LatestConflictManager(new LatestRevisionStrategy());
        } else if (resolutionStrategy.getConflictResolution() instanceof StrictConflictResolution) {
            Set<ForcedVersion> forcedVersions = ((StrictConflictResolution) resolutionStrategy.getConflictResolution()).getForcedVersions();
            DependencySelector selector = new DependencySelector(forcedVersions);
            return new ForceAwareStrictConflictManager(selector);
        } else {
            throw new RuntimeException("I don't know what ivy conflict manager to use for: " + resolutionStrategy);
        }
    }

    //TODO SF some tests around that
    public void maybeTranslateIvyResolveException(Exception e) {
        if (e instanceof StrictConflictException) {
            throw new GradleException("Your dependencies exhibit a version conflict. "
                    + "The conflict resolution strategy is set to: " + resolutionStrategy
                    + ". Details: " + e.getMessage(), e);
        }
    }
}
