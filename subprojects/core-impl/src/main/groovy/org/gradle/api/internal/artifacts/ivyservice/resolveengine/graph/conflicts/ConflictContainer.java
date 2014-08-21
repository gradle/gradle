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
import java.util.LinkedHashSet;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newLinkedHashMap;

/**
 * Generic container for conflicts. It's generic so that hopefully it's easier to comprehend (and test).
 */
class ConflictContainer<K, T> {

    final Map<K, Conflict> conflicts = newLinkedHashMap();
    private final Map<K, Collection<? extends T>> elements = newHashMap();
    private final Map<K, K> targetToSource = newLinkedHashMap();

    /**
     * Adds new element and returns a conflict instance if given element is conflicted. Element is conflicted when:
     *  - has more than 1 candidate
     *  - is in conflict with an existing element (via replacedBy relationship)
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

    private Conflict registerConflict(K target, K replacedBy) {
        //replacement candidates are the only important candidates
        Collection<? extends T> candidates = elements.get(replacedBy);
        assert candidates != null;

        Collection<K> participants = new LinkedHashSet<K>();
        participants.add(target);
        participants.add(replacedBy);

        //We need to ensure that the conflict is orderly injected to the list of conflicts
        //Brand new conflict goes to the end
        //If we find any matching conflict we have to hook up with it

        //Find an existing matching conflict
        for (K e : conflicts.keySet()) {
            Conflict c = conflicts.get(e);
            if (c.participants.contains(target) || c.participants.contains(replacedBy)) {
                //If there is already registered conflict with matching participants, hook up to this conflict
                c.candidates = candidates;
                c.participants.addAll(participants);
                return c;
            }
        }

        //No conflict with matching participants found, create new
        Conflict c = new Conflict(participants, candidates);
        conflicts.put(target, c);
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

    class Conflict implements ModuleConflict { //TODO SF should not implement ModuleConflict
        Collection<K> participants;
        Collection<? extends T> candidates;

        public Conflict(Collection<K> participants, Collection<? extends T> candidates) {
            this.participants = participants;
            this.candidates = candidates;
        }

        public void withAffectedModules(Action<ModuleIdentifier> affectedModulesAction) {
            for (K m : participants) {
                affectedModulesAction.execute((ModuleIdentifier) m);
            }
        }

        public String toString() {
            return Joiner.on(",").join(participants) + ":" + Joiner.on(",").join(candidates);
        }
    }
}
