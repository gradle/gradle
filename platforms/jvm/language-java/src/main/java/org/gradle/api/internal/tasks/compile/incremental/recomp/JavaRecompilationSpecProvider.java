/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.internal.file.Deleter;
import org.gradle.work.FileChange;

import java.util.Set;

public class JavaRecompilationSpecProvider extends AbstractRecompilationSpecProvider {

    private static final Set<String> SUPPORTED_FILE_EXTENSIONS = ImmutableSet.of(".java");

    public JavaRecompilationSpecProvider(
        Deleter deleter,
        FileOperations fileOperations,
        FileTree sourceTree,
        boolean incremental,
        Iterable<FileChange> sourceFileChanges
    ) {
        super(deleter, fileOperations, sourceTree, sourceFileChanges, incremental);
    }

    @Override
    protected Set<String> getFileExtensions() {
        return SUPPORTED_FILE_EXTENSIONS;
    }

    @Override
    protected void processCompilerSpecificDependencies(JavaCompileSpec spec, RecompilationSpec recompilationSpec, SourceFileChangeProcessor sourceFileChangeProcessor, SourceFileClassNameConverter sourceFileClassNameConverter) {
        // Nothing to do for Java compiler here
    }

    @Override
    protected boolean isIncrementalOnResourceChanges(CurrentCompilation currentCompilation) {
        return currentCompilation.getAnnotationProcessorPath().isEmpty();
    }
}
