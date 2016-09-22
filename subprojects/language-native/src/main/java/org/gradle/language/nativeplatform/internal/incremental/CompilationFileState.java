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

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;

/**
 * Immutable snapshot of the state of a source or header file.
 */
public class CompilationFileState {
    private final HashCode hash;
    private final IncludeDirectives includeDirectives;
    private final ImmutableSet<ResolvedInclude> resolvedIncludes;

    public CompilationFileState(HashCode hash, IncludeDirectives includeDirectives, ImmutableSet<ResolvedInclude> resolvedIncludes) {
        this.hash = hash;
        this.includeDirectives = includeDirectives;
        this.resolvedIncludes = resolvedIncludes;
    }

    public HashCode getHash() {
        return hash;
    }

    public IncludeDirectives getIncludeDirectives() {
        return includeDirectives;
    }

    public ImmutableSet<ResolvedInclude> getResolvedIncludes() {
        return resolvedIncludes;
    }
}
