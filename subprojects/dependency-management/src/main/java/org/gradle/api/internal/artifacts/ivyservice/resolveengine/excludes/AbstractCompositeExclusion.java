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

abstract class AbstractCompositeExclusion extends AbstractModuleExclusion {
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
        return implies(spec) && spec.implies(this);
    }

    @Override
    protected boolean doEquals(Object obj) {
        AbstractCompositeExclusion other = (AbstractCompositeExclusion) obj;
        return getFilters().equals(other.getFilters());
    }

    @Override
    protected int doHashCode() {
        return getFilters().hashCode();
    }

    /**
     * Returns true if for every spec in this spec, there is a corresponding spec in the given spec that excludesSameModulesAs().
     */
    protected boolean implies(AbstractCompositeExclusion spec) {
        for (AbstractModuleExclusion thisSpec : getFilters()) {
            boolean found = false;
            for (AbstractModuleExclusion otherSpec : spec.getFilters()) {
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
