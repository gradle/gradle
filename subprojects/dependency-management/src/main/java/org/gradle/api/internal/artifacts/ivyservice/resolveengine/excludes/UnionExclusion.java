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
import java.util.List;

/**
 * A filter that only excludes artifacts and modules that are excluded by _all_ of the supplied exclude rules.
 * As such, this is a union of the separate exclude rule filters.
 */
class UnionExclusion extends AbstractCompositeExclusion {
    private final List<AbstractModuleExclusion> filters;

    public UnionExclusion(List<AbstractModuleExclusion> filters) {
        this.filters = filters;
    }

    Collection<AbstractModuleExclusion> getFilters() {
        return filters;
    }

    /**
     * Can unpack into constituents when creating a larger union.
     */
    @Override
    protected void unpackUnion(Collection<AbstractModuleExclusion> specs) {
        specs.addAll(this.filters);
    }

    @Override
    protected boolean excludesNoModules() {
        for (AbstractModuleExclusion excludeSpec : filters) {
            if (excludeSpec.excludesNoModules()) {
                return true;
            }
        }
        return false;
    }

    public boolean excludeModule(ModuleIdentifier element) {
        for (AbstractModuleExclusion spec : filters) {
            if (!spec.excludeModule(element)) {
                return false;
            }
        }

        return true;
    }

    public boolean excludeArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        for (AbstractModuleExclusion spec : filters) {
            if (!spec.excludeArtifact(module, artifact)) {
                return false;
            }
        }

        return true;
    }

    public boolean mayExcludeArtifacts() {
        for (AbstractModuleExclusion spec : filters) {
            if (!spec.mayExcludeArtifacts()) {
                return false;
            }
        }

        return true;
    }
}
