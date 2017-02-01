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

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;

abstract class AbstractCompositeExclusion extends AbstractModuleExclusion {
    private int hashCode = -1;

    abstract Collection<AbstractModuleExclusion> getFilters();

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append(getClass().getSimpleName());
        for (AbstractModuleExclusion spec : getFilters()) {
            builder.append(' ');
            builder.append(spec);
        }
        builder.append("}");
        return builder.toString();
    }

    @Override
    protected boolean doExcludesSameModulesAs(AbstractModuleExclusion other) {
        AbstractCompositeExclusion spec = (AbstractCompositeExclusion) other;
        Collection<AbstractModuleExclusion> thisFilters = getFilters();
        Collection<AbstractModuleExclusion> otherFilters = spec.getFilters();

        // To make the comparison faster, we compute the sets of specs that exist in this exclusion, but not in the other
        // and the set of specs that exist in the other and not in this one. Then we only need to check if the missing
        // from one set have an equivalent in the missing of the other set, which is much faster than checking all of them
        Set<AbstractModuleExclusion> miss1 = Sets.newHashSetWithExpectedSize(thisFilters.size());
        Set<AbstractModuleExclusion> miss2 = Sets.newHashSetWithExpectedSize(otherFilters.size());
        for (AbstractModuleExclusion exclusion : thisFilters) {
            if (!otherFilters.contains(exclusion)) {
                miss1.add(exclusion);
            }
        }
        for (AbstractModuleExclusion otherFilter : otherFilters) {
            if (!thisFilters.contains(otherFilter)) {
                miss2.add(otherFilter);
            }
        }
        return (miss1.isEmpty() && miss2.isEmpty()) || (implies(miss1, miss2) && implies(miss2, miss1));
    }

    @Override
    protected boolean doEquals(Object obj) {
        AbstractCompositeExclusion other = (AbstractCompositeExclusion) obj;
        return hashCode() == other.hashCode() && getFilters().equals(other.getFilters());
    }

    @Override
    protected int doHashCode() {
        if (hashCode != -1) {
            return hashCode;
        }
        hashCode = getFilters().hashCode();
        return hashCode;
    }

    /**
     * Returns true if for every spec in this spec, there is a corresponding spec in the given spec that excludesSameModulesAs().
     */
    protected boolean implies(Set<AbstractModuleExclusion> miss1, Set<AbstractModuleExclusion> miss2) {
        if (miss1.isEmpty() || miss2.isEmpty()) {
            return false;
        }
        for (AbstractModuleExclusion thisSpec : miss1) {
            boolean found = false;
            for (AbstractModuleExclusion otherSpec : miss2) {
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
