/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.internal.file.Deleter;
import org.gradle.work.FileChange;

import java.util.Set;
import java.util.stream.Collectors;

public class GroovyRecompilationSpecProvider extends AbstractRecompilationSpecProvider {

    private static final Set<String> SUPPORTED_FILE_EXTENSIONS = ImmutableSet.of(".java", ".groovy");

    public GroovyRecompilationSpecProvider(
        Deleter deleter,
        FileOperations fileOperations,
        FileTree sources,
        boolean incremental,
        Iterable<FileChange> sourceChanges
    ) {
        super(deleter, fileOperations, sources, sourceChanges, incremental);
    }

    /**
     * For all classes with Java source that we will be recompiled due to some change, we need to recompile all subclasses.
     * This is because Groovy might try to load some subclass when analysing Groovy classes before Java compilation, but if parent class was stale,
     * it has been deleted, so class loading of a subclass will fail.
     *
     * Fix for issue <a href="https://github.com/gradle/gradle/issues/22531">#22531</a>.
     */
    @Override
    protected void processCompilerSpecificDependencies(
        JavaCompileSpec spec,
        RecompilationSpec recompilationSpec,
        SourceFileChangeProcessor sourceFileChangeProcessor,
        SourceFileClassNameConverter sourceFileClassNameConverter
    ) {
        if (!supportsGroovyJavaJointCompilation(spec)) {
            return;
        }
        Set<String> classesWithJavaSource = recompilationSpec.getClassesToCompile().stream()
            .flatMap(classToCompile -> sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile).stream())
            .filter(sourcePath -> sourcePath.endsWith(".java"))
            .flatMap(sourcePath -> sourceFileClassNameConverter.getClassNames(sourcePath).stream())
            .collect(Collectors.toSet());
        if (!classesWithJavaSource.isEmpty()) {
            // We need to collect just accessible dependents, since it seems
            // private references to classes are not problematic when Groovy compiler loads a class
            sourceFileChangeProcessor.processOnlyAccessibleChangeOfClasses(classesWithJavaSource, recompilationSpec);
        }
    }

    private boolean supportsGroovyJavaJointCompilation(JavaCompileSpec spec) {
        return spec instanceof GroovyJavaJointCompileSpec && ((GroovyJavaJointCompileSpec) spec).getGroovyCompileOptions().getFileExtensions().contains("java");
    }

    @Override
    protected Set<String> getFileExtensions() {
        return SUPPORTED_FILE_EXTENSIONS;
    }

    @Override
    protected boolean isIncrementalOnResourceChanges(CurrentCompilation currentCompilation) {
        return false;
    }
}
