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

import org.gradle.internal.hash.HashValue;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultIncludeDirectives;

import java.util.HashSet;
import java.util.Set;

public class CompilationFileState {
    private HashValue hash;
    private IncludeDirectives includeDirectives = new DefaultIncludeDirectives();
    private Set<ResolvedInclude> resolvedIncludes = new HashSet<ResolvedInclude>();

    public CompilationFileState(HashValue hash) {
        this.hash = hash;
    }

    public HashValue getHash() {
        return hash;
    }

    public IncludeDirectives getIncludeDirectives() {
        return includeDirectives;
    }

    public void setIncludeDirectives(IncludeDirectives includeDirectives) {
        this.includeDirectives = includeDirectives;
    }

    public Set<ResolvedInclude> getResolvedIncludes() {
        return resolvedIncludes;
    }

    public void setResolvedIncludes(Set<ResolvedInclude> resolvedIncludes) {
        this.resolvedIncludes = resolvedIncludes;
    }
}
