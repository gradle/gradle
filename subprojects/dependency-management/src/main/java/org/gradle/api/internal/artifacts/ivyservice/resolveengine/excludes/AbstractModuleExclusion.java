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

abstract class AbstractModuleExclusion implements ModuleExclusion {
    private static final String WILDCARD = "*";

    private int hashCode = -1;
    private ModuleExclusion lastCheck;
    private boolean lastCheckResult;

    protected static boolean isWildcard(String attribute) {
        return WILDCARD.equals(attribute);
    }

    public boolean excludeArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        return false;
    }

    public boolean mayExcludeArtifacts() {
        return false;
    }

    public final boolean excludesSameModulesAs(ModuleExclusion filter) {
        if (filter == this) {
            return true;
        }
        AbstractModuleExclusion other = (AbstractModuleExclusion) filter;
        boolean thisExcludesNothing = excludesNoModules();
        boolean otherExcludesNothing = other.excludesNoModules();
        if (thisExcludesNothing && otherExcludesNothing) {
            return true;
        }
        if (thisExcludesNothing || otherExcludesNothing) {
            return false;
        }
        if (!other.getClass().equals(getClass())) {
            return false;
        }
        synchronized (this) {
            // This is an optimization, based on the fact that in a large amount of times
            // a specific exclusion is checked against the same filter the next time so
            // we don't need to recompute the result: we can cache the last query
            if (filter == lastCheck) {
                return lastCheckResult;
            }
            lastCheck = other;
            lastCheckResult = doExcludesSameModulesAs(other);
            return lastCheckResult;
        }
    }

    /**
     * Only called when this and the other spec have the same class.
     */
    protected boolean doExcludesSameModulesAs(AbstractModuleExclusion other) {
        return false;
    }

    protected boolean excludesNoModules() {
        return false;
    }

    /**
     * Possibly unpack a composite spec into it's constituent parts, if those parts are applied as an intersection.
     * @param specs
     */
    protected void unpackIntersection(Collection<AbstractModuleExclusion> specs) {
        specs.add(this);
    }

    /**
     * Possibly unpack a composite spec into it's constituent parts, if those parts are applied as a union.
     */
    protected void unpackUnion(Collection<AbstractModuleExclusion> specs) {
        specs.add(this);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        return doEquals(obj);
    }

    protected abstract boolean doEquals(Object obj);

    @Override
    public final int hashCode() {
        if (hashCode != -1) {
            return hashCode;
        }
        hashCode = doHashCode();
        return hashCode;
    }

    protected abstract int doHashCode();
}
