/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BuildableCompilationState {
    private final Map<File, SourceFileState> sourceFileStates = new HashMap<File, SourceFileState>();

    public Set<File> getSourceInputs() {
        return sourceFileStates.keySet();
    }

    public void setState(File file, SourceFileState sourceFileState) {
        sourceFileStates.put(file, sourceFileState);
    }

    public CompilationState snapshot() {
        return new CompilationState(ImmutableMap.copyOf(sourceFileStates));
    }
}
