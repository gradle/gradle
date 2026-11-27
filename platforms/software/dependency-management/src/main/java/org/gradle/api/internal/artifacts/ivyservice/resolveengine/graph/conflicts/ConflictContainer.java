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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Generic container for conflicts. It's generic so that hopefully it's easier to comprehend (and test).
 */
class ConflictContainer<K> {

    final LinkedList<Set<K>> conflicts = new LinkedList<>();

    private final Set<K> seen = new HashSet<>();
    private final Multimap<K, K> replacementToReplaced = LinkedHashMultimap.create();

    /**
     * Adds the given element and returns the participants of its conflict if it is in
     * conflict with any other element seen previously.
     *
     * @param element the element to add
     * @param replacedBy optional element that replaces the given element
     */
    @Nullable
    public Set<K> newElement(K element, @Nullable K replacedBy) {
        seen.add(element);
        K replacesElement = null;
        if (replacedBy != null) {
            replacementToReplaced.put(replacedBy, element);
            if (seen.contains(replacedBy)) {
                replacesElement = replacedBy;
            }
        }
        Collection<K> elementReplaces = replacementToReplaced.get(element);
        if (elementReplaces.isEmpty() && replacesElement == null) {
            // We have not seen any other element that we replace or that replaces us.
            return null;
        }

        Set<K> participants = new LinkedHashSet<>(elementReplaces);
        participants.add(element);
        if (replacesElement != null) {
            participants.add(replacesElement);
        }

        //We need to ensure that the conflict is orderly injected to the list of conflicts
        //Brand new conflict goes to the end
        //If we find any matching conflicts we have to merge them into the earliest one
        Set<K> merged = null;
        Iterator<Set<K>> it = conflicts.iterator();
        while (it.hasNext()) {
            Set<K> c = it.next();
            if (!Collections.disjoint(c, participants)) {
                if (merged == null) {
                    merged = c;
                } else {
                    merged.addAll(c);
                    it.remove();
                }
            }
        }
        if (merged == null) {
            merged = participants;
            conflicts.add(merged);
        } else {
            merged.addAll(participants);
        }
        return merged;
    }

    public int getSize() {
        return conflicts.size();
    }

    public Set<K> popConflict() {
        assert !conflicts.isEmpty();
        return conflicts.pop();
    }

    public boolean isEmpty() {
        return conflicts.isEmpty();
    }

}
