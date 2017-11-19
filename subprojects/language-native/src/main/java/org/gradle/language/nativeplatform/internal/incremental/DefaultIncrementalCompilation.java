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

import org.gradle.language.nativeplatform.internal.IncludeDirectives;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIncrementalCompilation implements IncrementalCompilation {
    private final CompilationState finalState;
    private final List<File> recompile;
    private final List<File> removed;
    private final Set<File> discoveredInputs;
    private final Set<File> existingHeaders;
    private final boolean macroIncludesUsedInSources;
    private final Map<File, IncludeDirectives> sourceFileIncludeDirectives;

    public DefaultIncrementalCompilation(CompilationState finalState, List<File> recompile, List<File> removed, Set<File> discoveredInputs, Set<File> existingHeaders, boolean macroIncludesUsedInSources, Map<File, IncludeDirectives> sourceFileIncludeDirectives) {
        this.finalState = finalState;
        this.recompile = recompile;
        this.removed = removed;
        this.discoveredInputs = discoveredInputs;
        this.existingHeaders = existingHeaders;
        this.macroIncludesUsedInSources = macroIncludesUsedInSources;
        this.sourceFileIncludeDirectives = sourceFileIncludeDirectives;
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
    public Map<File, IncludeDirectives> getSourceFileIncludeDirectives() {
        return sourceFileIncludeDirectives;
    }

    @Override
    public CompilationState getFinalState() {
        return finalState;
    }

    @Override
    public Set<File> getDiscoveredInputs() {
        return discoveredInputs;
    }

    @Override
    public Set<File> getExistingHeaders() {
        return existingHeaders;
    }

    @Override
    public boolean isUnresolvedHeaders() {
        return macroIncludesUsedInSources;
    }
}
