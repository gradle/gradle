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
 * As such, this filter accepts the union of artifacts and modules accepted by the separate filters.
 */
class UnionExcludeRuleFilter extends AbstractCompositeExcludeRuleFilter {
    private final List<AbstractModuleExcludeRuleFilter> filters;

    public UnionExcludeRuleFilter(List<AbstractModuleExcludeRuleFilter> filters) {
        this.filters = filters;
    }

    Collection<AbstractModuleExcludeRuleFilter> getFilters() {
        return filters;
    }

    /**
     * Can unpack into constituents when creating a larger union.
     */
    @Override
    protected void unpackUnion(Collection<AbstractModuleExcludeRuleFilter> specs) {
        specs.addAll(this.filters);
    }

    @Override
    protected boolean acceptsAllModules() {
        for (AbstractModuleExcludeRuleFilter excludeSpec : filters) {
            if (excludeSpec.acceptsAllModules()) {
                return true;
            }
        }
        return false;
    }

    public boolean acceptModule(ModuleIdentifier element) {
        for (AbstractModuleExcludeRuleFilter spec : filters) {
            if (spec.acceptModule(element)) {
                return true;
            }
        }

        return false;
    }

    public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        for (AbstractModuleExcludeRuleFilter spec : filters) {
            if (spec.acceptArtifact(module, artifact)) {
                return true;
            }
        }

        return false;
    }

    public boolean acceptsAllArtifacts() {
        for (AbstractModuleExcludeRuleFilter spec : filters) {
            if (spec.acceptsAllArtifacts()) {
                return true;
            }
        }

        return false;
    }
}
