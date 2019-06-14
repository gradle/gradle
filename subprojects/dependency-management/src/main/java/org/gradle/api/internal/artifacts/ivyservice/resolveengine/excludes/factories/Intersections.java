/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

class Intersections {
    private final ExcludeFactory factory;

    public Intersections(ExcludeFactory factory) {
        this.factory = factory;
    }

    /**
     * Tries to compute an intersection of 2 specs.
     * The result MUST be a simplification, otherwise this method returns null.
     */
    ExcludeSpec tryIntersect(ExcludeSpec left, ExcludeSpec right) {
        if (left.equals(right)) {
            return left;
        }
        if (left instanceof GroupExclude) {
            return intersectGroup((GroupExclude) left, right);
        } else if (right instanceof GroupExclude) {
            return intersectGroup((GroupExclude) right, left);
        } else if (left instanceof ModuleExclude) {
            return intersectModule((ModuleExclude) left, right);
        } else if (right instanceof ModuleExclude) {
            return intersectModule((ModuleExclude) right, left);
        } else if (left instanceof GroupSetExclude) {
            return intersectGroupSet((GroupSetExclude) left, right);
        } else if (right instanceof GroupSetExclude) {
            return intersectGroupSet((GroupSetExclude) right, left);
        } else if (left instanceof ModuleIdSetExclude) {
            return intersectModuleIdSet((ModuleIdSetExclude) left, right);
        } else if (right instanceof ModuleIdSetExclude) {
            return intersectModuleIdSet((ModuleIdSetExclude) right, left);
        } else if (left instanceof ExcludeAnyOf) {
            return intersectAnyOf((ExcludeAnyOf) left, right);
        }
        return null;
    }

    private ExcludeSpec intersectAnyOf(ExcludeAnyOf left, ExcludeSpec right) {
        if (right instanceof ExcludeAnyOf) {
            Set<ExcludeSpec> common = Sets.newHashSet(left.getComponents());
            Set<ExcludeSpec> rightComponents = ((ExcludeAnyOf) right).getComponents();
            common.retainAll(rightComponents);
            if (common.size() >= 1) {
                Set<ExcludeSpec> toIntersect = Sets.newHashSet();
                for (ExcludeSpec component : left.getComponents()) {
                    if (!common.contains(component)) {
                        toIntersect.add(component);
                    }
                }
                for (ExcludeSpec component : rightComponents) {
                    if (!common.contains(component)) {
                        toIntersect.add(component);
                    }
                }
                ExcludeSpec alpha = common.size() == 1 ? common.iterator().next() : factory.anyOf(common);
                ExcludeSpec beta = toIntersect.size() == 1 ? toIntersect.iterator().next() : factory.allOf(toIntersect);
                return factory.anyOf(alpha, beta);
            }
        }
        return null;
    }

    private ExcludeSpec intersectModuleIdSet(ModuleIdSetExclude left, ExcludeSpec right) {
        Set<ModuleIdentifier> moduleIds = left.getModuleIds();
        if (right instanceof ModuleIdSetExclude) {
            Set<ModuleIdentifier> common = Sets.newHashSet(((ModuleIdSetExclude) right).getModuleIds());
            common.retainAll(moduleIds);
            return moduleIds(common);
        }
        return null;
    }

    private ExcludeSpec moduleIds(Set<ModuleIdentifier> common) {
        if (common.isEmpty()) {
            return factory.nothing();
        }
        if (common.size() == 1) {
            return factory.moduleId(common.iterator().next());
        }
        return factory.moduleIdSet(common);
    }

    private ExcludeSpec intersectGroup(GroupExclude left, ExcludeSpec right) {
        String group = left.getGroup();
        if (right instanceof GroupExclude) {
            // equality has been tested before so we know groups are different
            return factory.nothing();
        } else if (right instanceof ModuleIdExclude) {
            if (((ModuleIdExclude) right).getModuleId().getGroup().equals(group)) {
                return right;
            } else {
                return factory.nothing();
            }
        } else if (right instanceof GroupSetExclude) {
            if (((GroupSetExclude) right).getGroups().stream().anyMatch(g -> g.equals(group))) {
                return left;
            }
            return factory.nothing();
        } else if (right instanceof ModuleIdSetExclude) {
            Set<ModuleIdentifier> moduleIds = ((ModuleIdSetExclude) right).getModuleIds().stream().filter(id -> id.getGroup().equals(group)).collect(toSet());
            return moduleIdSet(moduleIds);
        }
        return null;
    }

    private ExcludeSpec moduleIdSet(Set<ModuleIdentifier> moduleIds) {
        if (moduleIds.isEmpty()) {
            return factory.nothing();
        }
        if (moduleIds.size() == 1) {
            return factory.moduleId(moduleIds.iterator().next());
        }
        return factory.moduleIdSet(moduleIds);
    }

    private ExcludeSpec intersectGroupSet(GroupSetExclude left, ExcludeSpec right) {
        Set<String> groups = left.getGroups();
        if (right instanceof GroupSetExclude) {
            Set<String> common = Sets.newHashSet(((GroupSetExclude) right).getGroups());
            common.retainAll(groups);
            return groupSet(common);
        } else if (right instanceof ModuleIdExclude) {
            if (groups.contains(((ModuleIdExclude) right).getModuleId().getGroup())) {
                return right;
            }
            return factory.nothing();
        } else if (right instanceof ModuleIdSetExclude) {
            Set<ModuleIdentifier> filtered = ((ModuleIdSetExclude) right).getModuleIds()
                .stream()
                .filter(id -> groups.contains(id.getGroup()))
                .collect(toSet());
            return moduleIdSet(filtered);
        }
        return null;
    }

    private ExcludeSpec groupSet(Set<String> common) {
        if (common.isEmpty()) {
            return factory.nothing();
        }
        if (common.size() == 1) {
            return factory.group(common.iterator().next());
        }
        return factory.groupSet(common);
    }


    private ExcludeSpec intersectModule(ModuleExclude left, ExcludeSpec right) {
        String module = left.getModule();
        if (right instanceof ModuleExclude) {
            if (((ModuleExclude) right).getModule().equals(module)) {
                return left;
            } else {
                return factory.nothing();
            }
        } else if (right instanceof ModuleIdExclude) {
            if (((ModuleIdExclude) right).getModuleId().getName().equals(module)) {
                return right;
            } else {
                return factory.nothing();
            }
        } else if (right instanceof ModuleSetExclude) {
            if (((ModuleSetExclude) right).getModules().stream().anyMatch(g -> g.equals(module))) {
                return left;
            }
            return factory.nothing();
        } else if (right instanceof ModuleIdSetExclude) {
            Set<ModuleIdentifier> common = ((ModuleIdSetExclude) right).getModuleIds().stream().filter(id -> id.getName().equals(module)).collect(toSet());
            return moduleIdSet(common);
        }
        return null;
    }
}
