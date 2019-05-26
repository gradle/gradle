/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.internal.hash.HashCode;

import java.util.Set;

import static java.util.Collections.unmodifiableSet;

/**
 * Immutable snapshot of the state of a source file.
 */
public class SourceFileState {
    private final HashCode hash;
    private final boolean hasUnresolved;
    private final Set<IncludeFileEdge> resolvedIncludes;

    public SourceFileState(HashCode hash, boolean hasUnresolved, Set<IncludeFileEdge> resolvedIncludes) {
        this.hash = hash;
        this.hasUnresolved = hasUnresolved;
        this.resolvedIncludes = unmodifiableSet(resolvedIncludes);
    }

    public HashCode getHash() {
        return hash;
    }

    /**
     * Were any edges in the include file graph unable to be resolved?
     */
    public boolean isHasUnresolved() {
        return hasUnresolved;
    }

    /**
     * The set of successfully resolved edges in the include file graph.
     */
    public Set<IncludeFileEdge> getEdges() {
        return resolvedIncludes;
    }
}
