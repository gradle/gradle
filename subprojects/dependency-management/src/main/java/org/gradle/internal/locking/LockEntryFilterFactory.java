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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class LockEntryFilterFactory {

    private static final LockEntryFilter FILTERS_NONE = new LockEntryFilter() {
        @Override
        public boolean filters(String moduleIdentifier) {
            return false;
        }
    };

    private static final LockEntryFilter FILTERS_ALL = new LockEntryFilter() {
        @Override
        public boolean filters(String moduleIdentifier) {
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
                if (!lockExclude.contains(MODULE_SEPARATOR)) {
                    throwInvalid(lockExclude);
                }
                if (lockExclude.contains(WILDCARD_SUFFIX)) {
                    lockEntryFilters.add(extractAdvancedLockEntryFilter(lockExclude));
                } else {
                    lockEntryFilters.add(new BasicLockEntryFilter(lockExclude));
                }
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

    private static LockEntryFilter throwInvalid(String lockExclude) {
        throw new IllegalArgumentException("Update lock format must be <group>:<artifact> but '" + lockExclude + "' is invalid.");
    }

    private static LockEntryFilter extractAdvancedLockEntryFilter(String lockExclude) {
        String[] split = lockExclude.split(MODULE_SEPARATOR);
        validateNotation(lockExclude, split);

        String group = split[0];
        String module = split[1];

        if (group.equals(WILDCARD_SUFFIX) && module.equals(WILDCARD_SUFFIX)) {
            return FILTERS_ALL;
        }

        if (module.equals(WILDCARD_SUFFIX)) {
            if (group.contains(WILDCARD_SUFFIX)) {
                return new GroupOnlyWildcardLockEntryFilter(group);
            } else {
                return new GroupStrictLockEntryFilter(group);
            }
        } else if (module.contains(WILDCARD_SUFFIX)) {
            if (group.equals(WILDCARD_SUFFIX)) {
                return new ModuleOnlyWildcardLockEntryFilter(module);
            } else if (group.contains(WILDCARD_SUFFIX)) {
                return new GroupModuleWildcardLockEntryFilter(group, module);
            } else {
                return new GroupStrictModuleWildcardLockEntryFilter(group, module);
            }
        } else if (group.equals("*")) {
            return new ModuleStrictLockEntryFilter(module);
        } else {
            return new GroupWildcardModuleStrictLockEntryFilter(group, module);
        }
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
        public boolean filters(String moduleIdentifier) {
            for (LockEntryFilter filter : filters) {
                if (filter.filters(moduleIdentifier)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class BasicLockEntryFilter implements LockEntryFilter {
        private final String lockExclude;

        public BasicLockEntryFilter(String lockExclude) {
            this.lockExclude = lockExclude;
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            return moduleIdentifier.startsWith(lockExclude);
        }
    }

    private static class GroupOnlyWildcardLockEntryFilter implements LockEntryFilter {
        private final String group;

        public GroupOnlyWildcardLockEntryFilter(String group) {
            this.group = group.substring(0, group.length() - 1);
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            return moduleIdentifier.startsWith(group);
        }
    }

    private static class GroupStrictLockEntryFilter implements LockEntryFilter {
        private final String group;

        public GroupStrictLockEntryFilter(String group) {
            this.group = group;
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            return moduleIdentifier.substring(0, moduleIdentifier.indexOf(MODULE_SEPARATOR)).equals(group);
        }
    }

    private static class ModuleOnlyWildcardLockEntryFilter implements LockEntryFilter {
        private final String module;

        public ModuleOnlyWildcardLockEntryFilter(String module) {
            this.module = module.substring(0, module.length() - 1);
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            String[] split = moduleIdentifier.split(MODULE_SEPARATOR);
            return split[1].startsWith(this.module);
        }
    }

    private static class GroupModuleWildcardLockEntryFilter implements LockEntryFilter {
        private final String group;
        private final String module;

        public GroupModuleWildcardLockEntryFilter(String group, String module) {
            this.group = group.substring(0, group.length() -1);
            this.module = module.substring(0, module.length() - 1);
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            String[] split = moduleIdentifier.split(MODULE_SEPARATOR);
            return split[0].startsWith(group) && split[1].startsWith(module);
        }
    }

    private static class GroupWildcardModuleStrictLockEntryFilter implements LockEntryFilter {
        private final String group;
        private final String module;

        public GroupWildcardModuleStrictLockEntryFilter(String group, String module) {
            this.group = group.substring(0, group.length() -1);
            this.module = module;
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            String[] split = moduleIdentifier.split(MODULE_SEPARATOR);
            return split[0].startsWith(group) && split[1].equals(module);
        }
    }

    private static class ModuleStrictLockEntryFilter implements LockEntryFilter {
        private final String module;

        public ModuleStrictLockEntryFilter(String module) {
            this.module = module;
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            String[] split = moduleIdentifier.split(MODULE_SEPARATOR);
            return split[1].equals(module);
        }
    }

    private static class GroupStrictModuleWildcardLockEntryFilter implements LockEntryFilter {
        private final String group;
        private final String module;

        public GroupStrictModuleWildcardLockEntryFilter(String group, String module) {
            this.group = group;
            this.module = module.substring(0, module.length() - 1);
        }

        @Override
        public boolean filters(String moduleIdentifier) {
            String[] split = moduleIdentifier.split(MODULE_SEPARATOR);
            return split[0].equals(group) && split[1].startsWith(module);
        }
    }
}
