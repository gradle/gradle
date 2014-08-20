/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.Collection;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newLinkedHashMap;

class ConflictContainer<T> {

    private final Map<ModuleIdentifier, Conflict> conflicts = newLinkedHashMap();
    private final Map<ModuleIdentifier, Collection<? extends T>> modules = newHashMap();
    private final Map<ModuleIdentifier, ModuleIdentifier> targetToSource = newLinkedHashMap();

    public Conflict newModule(ModuleIdentifier newModule, Collection<? extends T> candidates, ModuleIdentifier replacedBy) {
        modules.put(newModule, candidates);
        if (replacedBy != null) {
            targetToSource.put(replacedBy, newModule);
            if (modules.containsKey(replacedBy)) {
                //1) we've seen the replacement, register new conflict and return
                return registerConflict(newModule, replacedBy);
            }
        }

        ModuleIdentifier replacementSource = targetToSource.get(newModule);
        if (replacementSource != null) {
            //2) new module is a replacement to a module we've seen already, register conflict and return
            return registerConflict(replacementSource, newModule);
        }

        if (candidates.size() > 1) {
            //3) new module has more than 1 version, register conflict and return
            return registerConflict(newModule, null);
        }
        return null;
    }

    private Conflict registerConflict(ModuleIdentifier module, ModuleIdentifier replacedBy) {
        if (replacedBy == null) {
            Conflict c = new Conflict(module, modules.get(module));
            conflicts.put(module, c);
            return c;
        }
        Collection<? extends T> candidates = modules.get(replacedBy);
        assert candidates != null;
        conflicts.remove(replacedBy);
        Conflict c = new Conflict(module, replacedBy, candidates);
        conflicts.put(module, c);
        return c;
    }

    public int getSize() {
        return conflicts.size();
    }

    public Conflict pop() {
        ModuleIdentifier first = conflicts.keySet().iterator().next();
        return conflicts.remove(first);
    }

    class Conflict implements ModuleConflict {
        Collection<ModuleIdentifier> modules;
        Collection<? extends T> candidates;

        public Conflict(ModuleIdentifier module, Collection<? extends T> candidates) {
            this.modules = newArrayList(module);
            this.candidates = candidates;
        }

        public Conflict(ModuleIdentifier module, ModuleIdentifier replacedBy, Collection<? extends T> candidates) {
            this.modules = newArrayList(module, replacedBy);
            this.candidates = candidates;
        }

        public void withAffectedModules(Action<ModuleIdentifier> affectedModulesAction) {
            for (ModuleIdentifier m : modules) {
                affectedModulesAction.execute(m);
            }
        }
    }
}
