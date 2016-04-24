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

abstract class AbstractModuleExcludeRuleFilter implements ModuleExcludeRuleFilter {
    private static final String WILDCARD = "*";

    protected static boolean isWildcard(String attribute) {
        return WILDCARD.equals(attribute);
    }

    /**
     * Possibly unpack a composite spec into it's constituent parts, if those parts are applied as a union.
     */
    protected void unpackUnion(Collection<AbstractModuleExcludeRuleFilter> specs) {
        specs.add(this);
    }

    /**
     * Returns the union of this filter and the given filter. Returns null if not recognized.
     */
    protected AbstractModuleExcludeRuleFilter maybeMergeIntoUnion(AbstractModuleExcludeRuleFilter other) {
        return null;
    }

    public final boolean excludesSameModulesAs(ModuleExcludeRuleFilter filter) {
        if (filter == this) {
            return true;
        }
        AbstractModuleExcludeRuleFilter other = (AbstractModuleExcludeRuleFilter) filter;
        boolean thisAcceptsEverything = acceptsAllModules();
        boolean otherAcceptsEverything = other.acceptsAllModules();
        if (thisAcceptsEverything && otherAcceptsEverything) {
            return true;
        }
        if (thisAcceptsEverything ^ otherAcceptsEverything) {
            return false;
        }
        if (!other.getClass().equals(getClass())) {
            return false;
        }
        return doAcceptsSameModulesAs(other);
    }

    /**
     * Only called when this and the other spec have the same class.
     */
    protected boolean doAcceptsSameModulesAs(AbstractModuleExcludeRuleFilter other) {
        return false;
    }

    protected boolean acceptsAllModules() {
        return false;
    }

    /**
     * Possibly unpack a composite spec into it's constituent parts, if those parts are applied as an intersection.
     */
    protected void unpackIntersection(Collection<AbstractModuleExcludeRuleFilter> specs) {
        specs.add(this);
    }
}
