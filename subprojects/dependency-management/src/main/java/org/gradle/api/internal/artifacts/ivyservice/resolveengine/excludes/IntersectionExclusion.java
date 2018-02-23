/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;

/**
 * A spec that excludes modules or artifacts that are excluded by _any_ of the supplied exclusions.
 * As such, this is an intersection of the separate exclude rule filters.
 */
class IntersectionExclusion extends AbstractCompositeExclusion {
    private final ImmutableModuleExclusionSet excludeSpecs;
    private final boolean mergeable;

    private Boolean excludesNoModules;

    public IntersectionExclusion(ImmutableModuleExclusionSet specs) {
        this.excludeSpecs = specs;
        boolean canMerge = true;
        for (AbstractModuleExclusion spec : specs) {
            if (!canMerge(spec)) {
                canMerge = false;
                break;
            }
        }
        mergeable = canMerge;
    }

    boolean canMerge() {
        return mergeable;
    }

    ImmutableModuleExclusionSet getFilters() {
        return excludeSpecs;
    }

    @Override
    protected boolean excludesNoModules() {
        if (excludesNoModules == null) {
            // cache the result as we may be asked several times
            excludesNoModules = doExcludeNoModules();
        }
        return excludesNoModules;
    }

    private boolean doExcludeNoModules() {
        for (AbstractModuleExclusion excludeSpec : excludeSpecs) {
            if (!excludeSpec.excludesNoModules()) {
                return false;
            }
        }
        return true;
    }

    public boolean excludeModule(ModuleIdentifier element) {
        return excludeSpecs.excludesModule(element);
    }

    public boolean excludeArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        return excludeSpecs.excludesArtifact(module, artifact);
    }

    public boolean mayExcludeArtifacts() {
        for (AbstractModuleExclusion spec : excludeSpecs) {
            if (spec.mayExcludeArtifacts()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Can unpack into constituents when creating a larger intersection (since elements are applied as an intersection).
     * @param specs
     */
    @Override
    protected void unpackIntersection(Collection<AbstractModuleExclusion> specs) {
        specs.addAll(excludeSpecs);
    }

    private static boolean canMerge(AbstractModuleExclusion excludeSpec) {
        return excludeSpec instanceof ExcludeAllModulesSpec
            || excludeSpec instanceof ArtifactExcludeSpec
            || excludeSpec instanceof GroupNameExcludeSpec
            || excludeSpec instanceof ModuleNameExcludeSpec
            || excludeSpec instanceof ModuleIdExcludeSpec;
    }

}
