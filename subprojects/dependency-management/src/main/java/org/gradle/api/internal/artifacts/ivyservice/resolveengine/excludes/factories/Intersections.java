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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
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

        // Handle anyOf first because we don't want to special case it in
        // every other case

        if (left instanceof ExcludeAnyOf) {
            ExcludeSpec excludeSpec = intersectAnyOf((ExcludeAnyOf) left, right);
            if (excludeSpec != null) {
                return excludeSpec;
            }
        } else if (right instanceof ExcludeAnyOf) {
            ExcludeSpec excludeSpec = intersectAnyOf((ExcludeAnyOf) right, left);
            if (excludeSpec != null) {
                return excludeSpec;
            }
        }

        /*
         * The following cases are roughly ordered by the frequency of occurrence.  The order
         * these checks are performed if CRITICAL, as not all of the intersectXYZ methods consider
         * every type possibility for the right hand side operand.  Instead, some of these methods
         * assume they will be called in the order here, and thus that certain types will not ever
         * be supplied as their RHS operands.
         *
         * If the order of these checks is ever changed, then each intersectXYZ method must be
         * updated to handle a RHS operand in all possible subtypes.  There are currently 6 types
         * that extends ExcludeSpec and are relevant (GroupExclude, GroupSetExclude, ModuleExclude, ModuleIdExclude,
         * ModuleIdSetExclude, ModuleSetExclude).  ExcludeEverything and ExcludeNothing are special
         * cases handled above, and ArtifactExclude and CompositeExclude are also not relevant here.
         */
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
        } else if (left instanceof ModuleIdExclude) {
            return intersectModuleId((ModuleIdExclude) left, right);
        } else if (right instanceof ModuleIdExclude) {
            return intersectModuleId((ModuleIdExclude) right, left);
        } else if (left instanceof ModuleIdSetExclude) {
            return intersectModuleIdSet((ModuleIdSetExclude) left, right);
        } else if (right instanceof ModuleIdSetExclude) {
            return intersectModuleIdSet((ModuleIdSetExclude) right, left);
        } else if (left instanceof ModuleSetExclude) {
            return intersectModuleSet((ModuleSetExclude) left, right);
        } else if (right instanceof ModuleSetExclude) {
            return intersectModuleSet((ModuleSetExclude) right, left);
        }
        return null;
    }

    private ExcludeSpec intersectModuleSet(ModuleSetExclude left, ExcludeSpec right) {
        if (right instanceof ModuleSetExclude) {
            ModuleSetExclude msr = (ModuleSetExclude) right;
            Set<String> modules = Sets.newHashSet(left.getModules());
            modules.retainAll(msr.getModules());
            if (modules.isEmpty()) {
                return factory.nothing();
            }
            if (modules.size() == 1) {
                return factory.module(modules.iterator().next());
            }
            return factory.moduleSet(modules);
        }
        return null;
    }

    private ExcludeSpec intersectAnyOf(ExcludeAnyOf left, ExcludeSpec right) {
        Set<ExcludeSpec> leftComponents = left.getComponents();
        if (right instanceof ExcludeAnyOf) {
            Set<ExcludeSpec> rightComponents = ((ExcludeAnyOf) right).getComponents();
            Set<ExcludeSpec> common = Sets.newHashSet(leftComponents);
            common.retainAll(rightComponents);
            if (common.size() >= 1) {
                ExcludeSpec alpha = asUnion(common);
                if (leftComponents.equals(common) || rightComponents.equals(common)) {
                    return alpha;
                }
                Set<ExcludeSpec> remainderLeft = Sets.newHashSet(leftComponents);
                remainderLeft.removeAll(common);
                Set<ExcludeSpec> remainderRight = Sets.newHashSet(rightComponents);
                remainderRight.removeAll(common);

                ExcludeSpec unionLeft = asUnion(remainderLeft);
                ExcludeSpec unionRight = asUnion(remainderRight);
                ExcludeSpec beta = factory.allOf(unionLeft, unionRight);
                return factory.anyOf(alpha, beta);
            } else {
                // slowest path, full distribution
                // (A ∪ B) ∩ (C ∪ D) = (A ∩ C) ∪ (A ∩ D) ∪ (B ∩ C) ∪ (B ∩ D)
                Set<ExcludeSpec> intersections = Sets.newHashSetWithExpectedSize(leftComponents.size() * rightComponents.size());
                for (ExcludeSpec leftSpec : leftComponents) {
                    for (ExcludeSpec rightSpec : rightComponents) {
                        ExcludeSpec merged = tryIntersect(leftSpec, rightSpec);
                        if (merged == null) {
                            merged = factory.allOf(leftSpec, rightSpec);
                        }
                        if (!(merged instanceof ExcludeNothing)) {
                            intersections.add(merged);
                        }
                    }
                }
                return asUnion(intersections);
            }
        } else {
            // Here, we will distribute A ∩ (B ∪ C) if, and only if, at
            // least one of the distribution operations (A ∩ B) can be simplified
            ExcludeSpec[] excludeSpecs = leftComponents.toArray(new ExcludeSpec[0]);
            ExcludeSpec[] intersections = null;
            for (int i = 0; i < excludeSpecs.length; i++) {
                ExcludeSpec excludeSpec = tryIntersect(excludeSpecs[i], right);
                if (excludeSpec != null) {
                    if (intersections == null) {
                        intersections = new ExcludeSpec[excludeSpecs.length];
                    }
                    intersections[i] = excludeSpec;
                }
            }
            if (intersections != null) {
                Set<ExcludeSpec> simplified = Sets.newHashSetWithExpectedSize(excludeSpecs.length);
                for (int i = 0; i < intersections.length; i++) {
                    ExcludeSpec intersection = intersections[i];
                    if (intersection instanceof ExcludeNothing) {
                        continue;
                    }
                    if (intersection != null) {
                        simplified.add(intersection);
                    } else {
                        simplified.add(factory.allOf(excludeSpecs[i], right));
                    }
                }
                return asUnion(simplified);
            }
        }
        return null;
    }

    private ExcludeSpec asUnion(Set<ExcludeSpec> remainder) {
        if (remainder.isEmpty()) {
            // It's an intersection, and this method is always called on the remainder
            // of a reduction operation. If the remainder is empty then it means that
            // the intersection is empty
            return factory.nothing();
        }
        return remainder.size() == 1 ? remainder.iterator().next() : factory.anyOf(remainder);
    }

    private ExcludeSpec intersectModuleId(ModuleIdExclude left, ExcludeSpec right) {
        if (right instanceof ModuleIdExclude) {
            if (left.equals(right)) {
                return left;
            }
            return factory.nothing();
        } else if (right instanceof ModuleIdSetExclude) {
            Set<ModuleIdentifier> rightModuleIds = ((ModuleIdSetExclude) right).getModuleIds();
            if (rightModuleIds.contains(left.getModuleId())) {
                return left;
            }
            return factory.nothing();
        } else if (right instanceof ModuleSetExclude) {
            ModuleSetExclude moduleSetExclude = (ModuleSetExclude) right;
            if (moduleSetExclude.getModules().contains(left.getModuleId().getName())) {
                return left;
            } else {
                return factory.nothing();
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
        } else if (right instanceof ModuleSetExclude) {
            Set<String> modules = ((ModuleSetExclude) right).getModules();
            Set<ModuleIdentifier> identifiers = moduleIds.stream()
                .filter(e -> modules.contains(e.getName()))
                .collect(toSet());
            if (identifiers.isEmpty()) {
                return factory.nothing();
            }
            if (identifiers.size() == 1) {
                return factory.moduleId(identifiers.iterator().next());
            } else {
                return factory.moduleIdSet(identifiers);
            }
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
        } else if (right instanceof ModuleExclude) {
            return factory.moduleId(DefaultModuleIdentifier.newId(left.getGroup(), ((ModuleExclude) right).getModule()));
        } else if (right instanceof ModuleSetExclude) {
            ModuleSetExclude moduleSet = (ModuleSetExclude) right;
            return factory.moduleIdSet(moduleSet.getModules()
                .stream()
                .map(module -> DefaultModuleIdentifier.newId(left.getGroup(), module))
                .collect(toSet())
            );
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
        } else if (right instanceof  ModuleSetExclude) {
            return factory.moduleIdSet(groups.stream().flatMap(group -> ((ModuleSetExclude) right).getModules().stream().map(module -> DefaultModuleIdentifier.newId(group, module))).collect(toSet()));
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
        } else if (right instanceof GroupSetExclude) {
            return factory.moduleIdSet(((GroupSetExclude) right).getGroups().stream().map(group -> DefaultModuleIdentifier.newId(group, module)).collect(toSet()));
        }
        return null;
    }
}
