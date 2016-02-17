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

import java.io.File;
import java.util.List;
import java.util.Set;

public class DefaultIncrementalCompilation implements IncrementalCompilation {
    private final CompilationState finalState;
    private final List<File> recompile;
    private final List<File> removed;
    private final Set<File> discoveredInputs;

    public DefaultIncrementalCompilation(CompilationState finalState, List<File> recompile, List<File> removed, Set<File> discoveredInputs) {
        this.finalState = finalState;
        this.recompile = recompile;
        this.removed = removed;
        this.discoveredInputs = discoveredInputs;
    }

    @Override
    public List<File> getRecompile() {
        return recompile;
    }

    @Override
    public List<File> getRemoved() {
        return removed;
    }

    @Override
    public CompilationState getFinalState() {
        return finalState;
    }

    @Override
    public Set<File> getDiscoveredInputs() {
        return discoveredInputs;
    }
}
