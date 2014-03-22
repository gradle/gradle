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
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static java.util.Arrays.asList;

public class IncrementalCompilationInitializer {
    private final FileOperations fileOperations;

    public IncrementalCompilationInitializer(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    public void initializeCompilation(JavaCompileSpec spec, Collection<String> staleClasses) {
        PatternSet classesToDelete = new PatternSet();
        PatternSet sourceToCompile = new PatternSet();

        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            classesToDelete.include(path.concat(".class"));
            classesToDelete.include(path.concat("$*.class"));

            //the stale class might be a source class that was deleted
            //it's no harm to include it in sourceToCompile anyway
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
    }
}
