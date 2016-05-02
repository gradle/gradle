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

import java.util.Collection;

abstract class AbstractCompositeExcludeRuleFilter extends AbstractModuleExcludeRuleFilter {
    abstract Collection<AbstractModuleExcludeRuleFilter> getFilters();

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append(getClass().getSimpleName());
        for (AbstractModuleExcludeRuleFilter spec : getFilters()) {
            builder.append(' ');
            builder.append(spec);
        }
        builder.append("}");
        return builder.toString();
    }

    @Override
    protected boolean doAcceptsSameModulesAs(AbstractModuleExcludeRuleFilter other) {
        AbstractCompositeExcludeRuleFilter spec = (AbstractCompositeExcludeRuleFilter) other;
        return implies(spec) && spec.implies(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        AbstractCompositeExcludeRuleFilter other = (AbstractCompositeExcludeRuleFilter) obj;
        return getFilters().equals(other.getFilters());
    }

    @Override
    public int hashCode() {
        return getFilters().hashCode();
    }

    /**
     * Returns true if for every spec in this spec, there is a corresponding spec in the given spec that acceptsSameModulesAs().
     */
    protected boolean implies(AbstractCompositeExcludeRuleFilter spec) {
        for (AbstractModuleExcludeRuleFilter thisSpec : getFilters()) {
            boolean found = false;
            for (AbstractModuleExcludeRuleFilter otherSpec : spec.getFilters()) {
                if (thisSpec.excludesSameModulesAs(otherSpec)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
