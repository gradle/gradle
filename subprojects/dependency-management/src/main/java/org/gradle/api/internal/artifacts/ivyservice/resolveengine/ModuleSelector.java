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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.apache.ivy.core.module.descriptor.ExcludeRule;

import java.util.Arrays;
import java.util.Collection;

public class ModuleSelector implements Mergeable<ModuleSelector> {
    private final ModuleVersionSpec moduleVersionSpec;
    private final ArtifactVersionSpec artifactVersionSpec;

    public ModuleSelector(ModuleVersionSpec moduleVersionSpec, ArtifactVersionSpec artifactVersionSpec) {
        this.moduleVersionSpec = moduleVersionSpec;
        this.artifactVersionSpec = artifactVersionSpec;
    }

    public static ModuleSelector forExcludes(ExcludeRule... excludeRules) {
        return forExcludes(Arrays.asList(excludeRules));
    }

    public static ModuleSelector forExcludes(Collection<ExcludeRule> excludeRules) {
        ModuleVersionSpec mSpec = ModuleVersionSpec.forExcludes(excludeRules);
        ArtifactVersionSpec aSpec = ArtifactVersionSpec.forExcludes(excludeRules);
        return new ModuleSelector(mSpec, aSpec);
    }

    public ModuleVersionSpec getModuleVersionSpec() {
        return moduleVersionSpec;
    }

    public ArtifactVersionSpec getArtifactVersionSpec() {
        return artifactVersionSpec;
    }

    public ModuleSelector union(ModuleSelector moduleSelector) {
        ModuleVersionSpec mSpec = moduleVersionSpec.union(moduleSelector.getModuleVersionSpec());
        ArtifactVersionSpec aSpec = artifactVersionSpec.union(moduleSelector.getArtifactVersionSpec());
        return new ModuleSelector(mSpec, aSpec);
    }

    public ModuleSelector intersect(ModuleSelector moduleSelector) {
        ModuleVersionSpec mSpec = moduleVersionSpec.intersect(moduleSelector.getModuleVersionSpec());
        ArtifactVersionSpec aSpec = artifactVersionSpec.intersect(moduleSelector.getArtifactVersionSpec());
        return new ModuleSelector(mSpec, aSpec);
    }
}
