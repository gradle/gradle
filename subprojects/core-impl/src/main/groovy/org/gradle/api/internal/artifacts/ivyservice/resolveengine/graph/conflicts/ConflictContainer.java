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

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleIdentifier;

import java.util.Collection;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newLinkedHashSet;

/**
 * Generic container for conflicts.
 */
class ConflictContainer<K, T> {

    private final Map<K, Conflict> conflicts = newLinkedHashMap();
    private final Map<K, Collection<? extends T>> elements = newHashMap();
    private final Map<K, K> targetToSource = newLinkedHashMap();

    /**
     * Adds new element and returns a conflict instance if given element is conflicted
     *
     * @param target an element of some sort
     * @param candidates candidates for given element
     * @param replacedBy optional element that replaces the target
     */
    public Conflict newElement(K target, Collection<? extends T> candidates, @Nullable K replacedBy) {
        elements.put(target, candidates);
        if (replacedBy != null) {
            targetToSource.put(replacedBy, target);
            if (elements.containsKey(replacedBy)) {
                //1) we've seen the replacement, register new conflict and return
                return registerConflict(target, replacedBy);
            }
        }

        K replacementSource = targetToSource.get(target);
        if (replacementSource != null) {
            //2) new module is a replacement to a module we've seen already, register conflict and return
            return registerConflict(replacementSource, target);
        }

        if (candidates.size() > 1) {
            //3) new module has more than 1 version, register conflict and return
            return registerConflict(target, target);
        }
        return null;
    }

    private Conflict registerConflict(K module, K replacedBy) {
        Collection<? extends T> candidates = elements.get(replacedBy);
        assert candidates != null;
        conflicts.remove(replacedBy);
        Conflict c = new Conflict(module, replacedBy, candidates);
        conflicts.put(module, c);
        return c;
    }

    public int getSize() {
        return conflicts.size();
    }

    public Conflict popConflict() {
        assert !conflicts.isEmpty();
        K first = conflicts.keySet().iterator().next();
        return conflicts.remove(first);
    }

    class Conflict implements ModuleConflict {
        Collection<K> elements;
        Collection<? extends T> candidates;

        public Conflict(K target, K replacedBy, Collection<? extends T> candidates) {
            this.elements = newLinkedHashSet();
            this.elements.add(target);
            this.elements.add(replacedBy);
            this.candidates = candidates;
        }

        public void withAffectedModules(Action<ModuleIdentifier> affectedModulesAction) {
            for (K m : elements) {
                affectedModulesAction.execute((ModuleIdentifier) m);
            }
        }

        public String toString() {
            return Joiner.on(",").join(elements) + ":" + Joiner.on(",").join(candidates);
        }
    }
}
