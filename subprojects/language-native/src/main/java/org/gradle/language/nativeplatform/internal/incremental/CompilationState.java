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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.File;

/**
 * An immutable snapshot of compilation state.
 */
public class CompilationState {
    private final ImmutableSet<File> sourceInputs;
    private final ImmutableMap<File, CompilationFileState> fileStates;

    public CompilationState(ImmutableSet<File> sourceInputs, ImmutableMap<File, CompilationFileState> fileStates) {
        this.sourceInputs = sourceInputs;
        this.fileStates = fileStates;
    }

    public CompilationState() {
        sourceInputs = ImmutableSet.of();
        fileStates = ImmutableMap.of();
    }

    public ImmutableSet<File> getSourceInputs() {
        return sourceInputs;
    }

    public ImmutableMap<File, CompilationFileState> getFileStates() {
        return fileStates;
    }

    public CompilationFileState getState(File file) {
        return fileStates.get(file);
    }
}
