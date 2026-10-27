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
     * For all classes that we will recompile due to some change, we need to recompile their accessible dependents as well.
     * Groovy may load classes of the previous compilation from the classpath, not only read them: the compiler does so when it
     * analyzes Groovy classes before Java compilation, and an AST transformation does so whenever it asks a classpath class for
     * its {@code ClassNode#getTypeClass()}. Loading a class loads the classes it refers to, and a class being recompiled has been
     * deleted from the output, so loading a stale class that refers to it fails with a {@code NoClassDefFoundError}.
     * <p>
     * A class recompiled because its own source changed already brings its accessible dependents along; this adds them for
     * the classes recompiled only because a private dependency changed.
     *
     * Fix for issues <a href="https://github.com/gradle/gradle/issues/22531">#22531</a> and
     * <a href="https://github.com/gradle/gradle/issues/22519">#22519</a>.
     */
    @Override
    protected void processCompilerSpecificDependencies(
        JavaCompileSpec spec,
        RecompilationSpec recompilationSpec,
        SourceFileChangeProcessor sourceFileChangeProcessor,
        SourceFileClassNameConverter sourceFileClassNameConverter
    ) {
        Set<String> recompiledClasses = recompilationSpec.getClassesToCompile().stream()
            .flatMap(classToCompile -> sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile).stream())
            .flatMap(sourcePath -> sourceFileClassNameConverter.getClassNames(sourcePath).stream())
            .collect(Collectors.toSet());
        if (!recompiledClasses.isEmpty()) {
            // We need to collect just accessible dependents, since it seems
            // private references to classes are not problematic when Groovy compiler loads a class
            sourceFileChangeProcessor.processOnlyAccessibleChangeOfClasses(recompiledClasses, recompilationSpec);
        }
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
