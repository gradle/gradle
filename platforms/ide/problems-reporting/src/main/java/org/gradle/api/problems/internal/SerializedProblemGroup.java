/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.ProblemGroup;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Serialized form of the predefined-hierarchy groups: only the path of names from the root. On reading, the path is resolved
 * against the predefined hierarchy, so predefined groups come back as the canonical instances (with their descriptions) and
 * user-defined groups are re-created below them. This keeps the payload small: a group never drags its siblings along.
 */
final class SerializedProblemGroup implements Serializable {

    private final ImmutableList<String> path;

    private SerializedProblemGroup(ImmutableList<String> path) {
        this.path = path;
    }

    static SerializedProblemGroup of(ProblemGroup group) {
        ImmutableList.Builder<String> leafToRoot = ImmutableList.builder();
        for (ProblemGroup current = group; current != null; current = current.getParent()) {
            leafToRoot.add(current.getName());
        }
        return new SerializedProblemGroup(leafToRoot.build().reverse());
    }

    private Object readResolve() throws ObjectStreamException {
        ResolvableProblemGroup current = DefaultProblemGroups.INSTANCE.findRoot(path.get(0));
        if (current == null) {
            throw new InvalidObjectException("Unknown predefined root problem group '" + path.get(0) + "'");
        }
        for (int i = 1; i < path.size(); i++) {
            current = current.resolveChild(path.get(i));
        }
        return current;
    }
}
