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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;

import java.util.Set;
import java.util.stream.Collectors;

class Unions {
    private final ExcludeFactory factory;

    public Unions(ExcludeFactory factory) {
        this.factory = factory;
    }

    /**
     * Tries to compute an union of 2 specs.
     * The result MUST be a simplification, otherwise this method returns null.
     */
    ExcludeSpec tryUnion(ExcludeSpec left, ExcludeSpec right) {
        if (left.equals(right)) {
            return left;
        }
        if (left instanceof ModuleExclude) {
            return tryModuleUnion((ModuleExclude) left, right);
        } else if (right instanceof ModuleExclude) {
            return tryModuleUnion((ModuleExclude) right, left);
        }
        if (left instanceof GroupExclude) {
            return tryGroupUnion((GroupExclude) left, right);
        } else if (right instanceof GroupExclude) {
            return tryGroupUnion((GroupExclude) right, left);
        }
        if (left instanceof ModuleSetExclude) {
            return tryModuleSetUnion((ModuleSetExclude) left, right);
        } else if (right instanceof ModuleSetExclude) {
            return tryModuleSetUnion((ModuleSetExclude) right, left);
        }
        if (left instanceof GroupSetExclude) {
            return tryGroupSetUnion((GroupSetExclude) left, right);
        } else if (right instanceof GroupSetExclude) {
            return tryGroupSetUnion((GroupSetExclude) right, left);
        }
        return null;
    }

    private ExcludeSpec tryModuleUnion(ModuleExclude left, ExcludeSpec right) {
        String leftModule = left.getModule();
        if (right instanceof ModuleIdExclude) {
            ModuleIdExclude mie = (ModuleIdExclude) right;
            if (mie.getModuleId().getName().equals(leftModule)) {
                return left;
            }
        }
        if (right instanceof ModuleIdSetExclude) {
            ModuleIdSetExclude ids = (ModuleIdSetExclude) right;
            Set<ModuleIdentifier> items = ids.getModuleIds().stream().filter(id -> !id.getName().equals(leftModule)).collect(Collectors.toSet());
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()));
            }
            if (items.isEmpty()) {
                return left;
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items));
            }
        }
        return null;
    }

    private ExcludeSpec tryGroupUnion(GroupExclude left, ExcludeSpec right) {
        String leftGroup = left.getGroup();
        if (right instanceof ModuleIdExclude) {
            ModuleIdExclude mie = (ModuleIdExclude) right;
            if (mie.getModuleId().getGroup().equals(leftGroup)) {
                return left;
            }
        }
        if (right instanceof ModuleIdSetExclude) {
            ModuleIdSetExclude ids = (ModuleIdSetExclude) right;
            Set<ModuleIdentifier> items = ids.getModuleIds().stream().filter(id -> !id.getGroup().equals(leftGroup)).collect(Collectors.toSet());
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()));
            }
            if (items.isEmpty()) {
                return left;
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items));
            }
        }
        return null;
    }

    private ExcludeSpec tryModuleSetUnion(ModuleSetExclude left, ExcludeSpec right) {
        Set<String> leftModules = left.getModules();
        if (right instanceof ModuleIdExclude) {
            ModuleIdExclude mie = (ModuleIdExclude) right;
            if (leftModules.contains(mie.getModuleId().getName())) {
                return left;
            }
        }
        if (right instanceof ModuleIdSetExclude) {
            ModuleIdSetExclude ids = (ModuleIdSetExclude) right;
            Set<ModuleIdentifier> items = ids.getModuleIds().stream().filter(id -> !leftModules.contains(id.getName())).collect(Collectors.toSet());
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()));
            }
            if (items.isEmpty()) {
                return left;
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items));
            }
        }
        return null;
    }

    private ExcludeSpec tryGroupSetUnion(GroupSetExclude left, ExcludeSpec right) {
        Set<String> leftGroups = left.getGroups();
        if (right instanceof ModuleIdExclude) {
            ModuleIdExclude mie = (ModuleIdExclude) right;
            if (leftGroups.contains(mie.getModuleId().getGroup())) {
                return left;
            }
        }
        if (right instanceof ModuleIdSetExclude) {
            ModuleIdSetExclude ids = (ModuleIdSetExclude) right;
            Set<ModuleIdentifier> items = ids.getModuleIds().stream().filter(id -> !leftGroups.contains(id.getGroup())).collect(Collectors.toSet());
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()));
            }
            if (items.isEmpty()) {
                return left;
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items));
            }
        }
        return null;
    }

}
