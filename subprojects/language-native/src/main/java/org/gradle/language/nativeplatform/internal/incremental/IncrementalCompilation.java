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

public interface IncrementalCompilation {
    List<File> getRecompile();

    List<File> getRemoved();

    CompilationState getFinalState();

    /**
     * The include directives for those source files that are to be recompiled.
     */
    Map<File, IncludeDirectives> getSourceFileIncludeDirectives();

    /**
     * The set of all input locations that were discovered as part of resolving the dependencies for this compilation.
     */
    Set<File> getDiscoveredInputs();

    Set<File> getExistingHeaders();

    boolean isUnresolvedHeaders();
}
