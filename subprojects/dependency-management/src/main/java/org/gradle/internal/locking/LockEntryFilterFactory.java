/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class LockEntryFilterFactory {

    private static final LockEntryFilter FILTERS_NONE = new LockEntryFilter() {
        @Override
        public boolean isSatisfiedBy(ModuleComponentIdentifier element) {
            return false;
        }

    };

    private static final LockEntryFilter FILTERS_ALL = new LockEntryFilter() {
        @Override
        public boolean isSatisfiedBy(ModuleComponentIdentifier element) {
            return true;
        }

    };
    private static final String WILDCARD_SUFFIX = "*";
    public static final String MODULE_SEPARATOR = ":";

    static LockEntryFilter forParameter(List<String> lockedDependenciesToUpdate) {
        if (lockedDependenciesToUpdate.isEmpty()) {
            return FILTERS_NONE;
        }
        HashSet<LockEntryFilter> lockEntryFilters = new HashSet<LockEntryFilter>();
        for (String lockExcludes : lockedDependenciesToUpdate) {
            for (String lockExclude : lockExcludes.split(",")) {
                String[] split = lockExclude.split(MODULE_SEPARATOR);
                validateNotation(lockExclude, split);

                lockEntryFilters.add(createFilter(split[0], split[1]));
            }
            if (lockEntryFilters.isEmpty()) {
                throwInvalid(lockExcludes);
            }
        }

        if (lockEntryFilters.size() == 1) {
            return lockEntryFilters.iterator().next();
        } else {
            return new AggregateLockEntryFilter(lockEntryFilters);
        }
    }

    private static void throwInvalid(String lockExclude) {
        throw new IllegalArgumentException("Update lock format must be <group>:<artifact> but '" + lockExclude + "' is invalid.");
    }

    private static LockEntryFilter createFilter(final String group, final String module) {
        if (group.equals(WILDCARD_SUFFIX) && module.equals(WILDCARD_SUFFIX)) {
            return FILTERS_ALL;
        }

        return new GroupModuleLockEntryFilter(group, module);
    }

    private static void validateNotation(String lockExclude, String[] split) {
        if (split.length != 2) {
            throwInvalid(lockExclude);
        }
        String group = split[0];
        String module = split[1];

        if ((group.contains(WILDCARD_SUFFIX) && !group.endsWith(WILDCARD_SUFFIX)) || (module.contains(WILDCARD_SUFFIX) && !module.endsWith(WILDCARD_SUFFIX))) {
            throwInvalid(lockExclude);
        }

    }

    private static class AggregateLockEntryFilter implements LockEntryFilter {
        private final Set<LockEntryFilter> filters;

        private AggregateLockEntryFilter(Set<LockEntryFilter> filters) {
            this.filters = filters;
        }

        @Override
        public boolean isSatisfiedBy(ModuleComponentIdentifier moduleComponentIdentifier) {
            for (LockEntryFilter filter : filters) {
                if (filter.isSatisfiedBy(moduleComponentIdentifier)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class GroupModuleLockEntryFilter implements LockEntryFilter {
        private final String group;
        private final String module;

        private GroupModuleLockEntryFilter(String group, String module) {
            this.group = group;
            this.module = module;
        }

        @Override
        public boolean isSatisfiedBy(ModuleComponentIdentifier id) {
            return matches(group, id.getGroup()) && matches(module, id.getModule());
        }

        private boolean matches(String test, String candidate) {
            if (test.endsWith(WILDCARD_SUFFIX)) {
                return candidate.startsWith(test.substring(0, test.length() - 1));
            }
            return candidate.equals(test);
        }
    }
}
