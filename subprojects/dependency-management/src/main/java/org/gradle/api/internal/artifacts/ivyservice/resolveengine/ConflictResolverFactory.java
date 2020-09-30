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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;

public class ConflictResolverFactory {
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
    private final FeaturePreviews featurePreviews;

    public ConflictResolverFactory(VersionComparator versionComparator, VersionParser versionParser, FeaturePreviews featurePreviews) {
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.featurePreviews = featurePreviews;
    }

    public ModuleConflictResolver<ComponentState> createConflictResolver(ConflictResolution conflictResolution) {
        boolean releaseBias = true;
        if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.VERSION_ORDERING_V2)) {
            releaseBias = false;
        }
        ModuleConflictResolver<ComponentState> conflictResolver = new LatestModuleConflictResolver<>(versionComparator, versionParser, releaseBias);
        if (conflictResolution == ConflictResolution.preferProjectModules) {
            conflictResolver = new ProjectDependencyForcingResolver<>(conflictResolver);
        }
        return conflictResolver;
    }
}
