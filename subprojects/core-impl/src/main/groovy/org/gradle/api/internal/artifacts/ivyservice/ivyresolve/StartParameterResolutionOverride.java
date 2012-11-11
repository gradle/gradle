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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

import java.util.concurrent.TimeUnit;

public class StartParameterResolutionOverride {
    private final StartParameter startParameter;

    public StartParameterResolutionOverride(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public void addResolutionRules(ResolutionRules resolutionRules) {
        if (startParameter.isOffline()) {
            resolutionRules.eachDependency(new Action<DependencyResolutionControl>() {
                public void execute(DependencyResolutionControl dependencyResolutionControl) {
                    dependencyResolutionControl.useCachedResult();
                }
            });
            resolutionRules.eachModule(new Action<ModuleResolutionControl>() {
                public void execute(ModuleResolutionControl moduleResolutionControl) {
                    moduleResolutionControl.useCachedResult();
                }
            });
            resolutionRules.eachArtifact(new Action<ArtifactResolutionControl>() {
                public void execute(ArtifactResolutionControl artifactResolutionControl) {
                    artifactResolutionControl.useCachedResult();
                }
            });
        } else if (startParameter.isRefreshDependencies()) {
            resolutionRules.eachDependency(new Action<DependencyResolutionControl>() {
                public void execute(DependencyResolutionControl dependencyResolutionControl) {
                    dependencyResolutionControl.cacheFor(0, TimeUnit.SECONDS);
                }
            });
            resolutionRules.eachModule(new Action<ModuleResolutionControl>() {
                public void execute(ModuleResolutionControl moduleResolutionControl) {
                    moduleResolutionControl.cacheFor(0, TimeUnit.SECONDS);
                }
            });
            resolutionRules.eachArtifact(new Action<ArtifactResolutionControl>() {
                public void execute(ArtifactResolutionControl artifactResolutionControl) {
                    artifactResolutionControl.cacheFor(0, TimeUnit.SECONDS);
                }
            });
        }
    }

    public ModuleVersionRepository overrideModuleVersionRepository(ModuleVersionRepository original) {
        if (startParameter.isOffline() && !original.isLocal()) {
            return new OfflineModuleVersionRepository(original);
        }
        return original;
    }

    private static class OfflineModuleVersionRepository implements ModuleVersionRepository {
        private final ModuleVersionRepository original;

        public OfflineModuleVersionRepository(ModuleVersionRepository original) {
            this.original = original;
        }

        public String getId() {
            return original.getId();
        }

        public String getName() {
            return original.getName();
        }

        public boolean isLocal() {
            return false;
        }

        public void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionDescriptor result) {
            result.failed(new ModuleVersionResolveException("No cached version available for offline mode"));
        }

        public void resolve(Artifact artifact, BuildableArtifactResolveResult result) {
            result.failed(new ArtifactResolveException(artifact, "No cached version available for offline mode"));
        }
    }
}
