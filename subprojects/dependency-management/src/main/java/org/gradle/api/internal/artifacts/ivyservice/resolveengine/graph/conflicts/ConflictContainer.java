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
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.singletonList;

/**
 * Generic container for conflicts. It's generic so that hopefully it's easier to comprehend (and test).
 */
class ConflictContainer<K, T> {

    final LinkedList<Conflict> conflicts = newLinkedList();
    private final Map<K, Conflict> conflictsByParticipant = Maps.newHashMap();

    private final Map<K, Collection<? extends T>> elements = newHashMap();
    private final Multimap<K, K> targetToSource = LinkedHashMultimap.create();

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
        if (candidates.isEmpty()) {
            return null;
        }
        elements.put(target, candidates);
        if (replacedBy != null) {
            targetToSource.put(replacedBy, target);
            if (elements.containsKey(replacedBy)) {
                //1) we've seen the replacement, register new conflict and return
                return registerConflict(target, replacedBy);
            }
        }

        Collection<K> replacementSource = targetToSource.get(target);
        if (!replacementSource.isEmpty()) {
            //2) new module is a replacement to a module we've seen already, register conflict and return
            return registerConflict(replacementSource, target);
        }

        if (candidates.size() > 1) {
            //3) new module has more than 1 version, register conflict and return
            return registerConflict(target, target);
        }
        return null;
    }

    private Conflict registerConflict(Collection<K> targets, K replacedBy) {
        assert !targets.isEmpty();

        //replacement candidates are the only important candidates
        Collection<? extends T> candidates = elements.get(replacedBy);
        assert candidates != null;

        Set<K> participants = new LinkedHashSet<>(targets);
        participants.add(replacedBy);

        //We need to ensure that the conflict is orderly injected to the list of conflicts
        //Brand new conflict goes to the end
        //If we find any matching conflict we have to hook up with it

        //Find an existing matching conflict
        for (K participant : participants) {
            Conflict c = conflictsByParticipant.get(participant);
            if (c != null) {
                //there is already registered conflict with at least one matching participant, hook up to this conflict
                c.candidates = candidates;
                c.participants.addAll(participants);
                return c;
            }
        }

        //No conflict with matching participants found, create new
        Conflict c = new Conflict(participants, candidates);
        conflicts.add(c);
        for (K participant : participants) {
            conflictsByParticipant.put(participant, c);
        }
        return c;
    }

    private Conflict registerConflict(K target, K replacedBy) {
        return registerConflict(singletonList(target), replacedBy);
    }

    public int getSize() {
        return conflicts.size();
    }

    public Conflict popConflict() {
        assert !conflicts.isEmpty();
        Conflict conflict = conflicts.pop();
        for (K participant : conflict.participants) {
            conflictsByParticipant.remove(participant);
        }
        return conflict;
    }

    public boolean isEmpty() {
        return conflicts.isEmpty();
    }

    class Conflict {
        final Set<K> participants;
        Collection<? extends T> candidates;

        public Conflict(Set<K> participants, Collection<? extends T> candidates) {
            this.participants = participants;
            this.candidates = candidates;
        }

        public String toString() {
            return Joiner.on(",").join(participants) + ":" + Joiner.on(",").join(candidates);
        }
    }
}
