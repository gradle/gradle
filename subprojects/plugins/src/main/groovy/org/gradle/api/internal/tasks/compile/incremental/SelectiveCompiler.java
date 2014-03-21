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

package org.gradle.api.internal.tasks.compile.incremental;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.Clock;

import java.io.File;
import java.util.Set;

import static java.util.Arrays.asList;

public class SelectiveCompiler implements org.gradle.api.internal.tasks.compile.Compiler<JavaCompileSpec> {
    private static final Logger LOG = Logging.getLogger(SelectiveCompiler.class);
    private final IncrementalTaskInputs inputs;
    private final CleaningJavaCompiler cleaningCompiler;
    private final FileOperations fileOperations;
    private final StaleClassesDetecter staleClassesDetecter;

    public SelectiveCompiler(IncrementalTaskInputs inputs, CleaningJavaCompiler cleaningCompiler,
                             FileOperations fileOperations, StaleClassesDetecter staleClassesDetecter) {
        this.inputs = inputs;
        this.cleaningCompiler = cleaningCompiler;
        this.fileOperations = fileOperations;
        this.staleClassesDetecter = staleClassesDetecter;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        Clock clock = new Clock();
        StaleClasses staleClasses = staleClassesDetecter.detectStaleClasses(inputs);

        if (staleClasses.isFullRebuildNeeded()) {
            LOG.lifecycle("Stale classes detection completed in {}. Full rebuild is needed due to: {}.", clock.getTime(), staleClasses.getFullRebuildReason());
            return cleaningCompiler.execute(spec);
        }

        if (staleClasses.getClassNames().isEmpty()) {
            //hurray! Compilation not needed!
            return new WorkResult() {
                public boolean getDidWork() {
                    return true;
                }
            };
        }

        PatternSet classesToDelete = new PatternSet();
        PatternSet sourceToCompile = new PatternSet();

        for (String staleClass : staleClasses.getClassNames()) {
            String path = staleClass.replaceAll("\\.", "/");
            classesToDelete.include(path.concat(".class")); //TODO SF remember about inner classes
            sourceToCompile.include(path.concat(".java"));
        }

        //selectively configure the source
        spec.setSource(spec.getSource().getAsFileTree().matching(sourceToCompile));
        //since we're compiling selectively we need to include the classes compiled previously
        spec.setClasspath(Iterables.concat(spec.getClasspath(), asList(spec.getDestinationDir())));
        //get rid of stale files
        Set<File> staleClassFiles = fileOperations.fileTree(spec.getDestinationDir()).matching(classesToDelete).getFiles();
        for (File staleClassFile : staleClassFiles) {
            staleClassFile.delete();
        }

        try {
            //use the original compiler to avoid cleaning up all the files
            return cleaningCompiler.getCompiler().execute(spec);
        } finally {
            LOG.lifecycle("Stale classes detection completed in {}. Stale classes: {}, Compile include patterns: {}, Files to delete: {}",
                    clock.getTime(), classesToDelete.getIncludes().size(), sourceToCompile.getIncludes(), staleClassFiles.size());
        }
    }
}
