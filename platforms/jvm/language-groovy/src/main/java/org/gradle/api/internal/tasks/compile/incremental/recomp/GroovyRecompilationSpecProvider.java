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
import org.gradle.internal.file.Deleter;
import org.gradle.work.FileChange;

import java.util.HashSet;
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
     * Groovy may load classes of the previous compilation from the classpath, not only read them: the compiler does so for the
     * helper classes of a precompiled trait, and an AST transformation does so whenever it asks a classpath class for its
     * {@code ClassNode#getTypeClass()}. The JVM then resolves the classes they refer to, whatever the visibility of the
     * reference, and finds the classes being recompiled in their stashed class files, see
     * {@code JavaCompileSpec#getStashedClassFiles()}. Nothing more is recompiled for that.
     * <p>
     * A recompiled class with a Java source needs more. It is not part of the Groovy compilation unit, and its class file is
     * out of the output while it is recompiled, so the compiler cannot resolve it at all until it has run the Java compiler.
     * The stashed class file is of no help here: it is kept away from the compiler on purpose, as it shows the class as it was
     * before the change. The compiler reads the classes of the previous output it meets, and fails with a
     * {@code NoClassDefFoundError} on any member of theirs, private or not, whose type is such a class. So all of its
     * dependents are recompiled, repeatedly while that brings in further classes with Java sources.
     *
     * Fix for issues <a href="https://github.com/gradle/gradle/issues/22531">#22531</a> and
     * <a href="https://github.com/gradle/gradle/issues/22814">#22814</a>.
     */
    @Override
    protected void processCompilerSpecificDependencies(
        RecompilationSpec recompilationSpec,
        SourceFileChangeProcessor sourceFileChangeProcessor,
        SourceFileClassNameConverter sourceFileClassNameConverter
    ) {
        Set<String> examinedClasses = new HashSet<>();
        while (!recompilationSpec.isFullRebuildNeeded()) {
            Set<String> newClasses = new HashSet<>(recompilationSpec.getClassesToCompile());
            newClasses.removeAll(examinedClasses);
            if (newClasses.isEmpty()) {
                return;
            }
            examinedClasses.addAll(newClasses);
            Set<String> classesWithJavaSource = classesWithJavaSource(newClasses, sourceFileClassNameConverter);
            if (!classesWithJavaSource.isEmpty()) {
                sourceFileChangeProcessor.processDependentsOfRecompiledClasses(classesWithJavaSource, recompilationSpec);
                examinedClasses.addAll(classesWithJavaSource);
            }
        }
    }

    @Override
    protected boolean needsStashedClassFiles() {
        return true;
    }

    /**
     * The classes of the Java sources of the given classes.
     */
    private static Set<String> classesWithJavaSource(Set<String> classes, SourceFileClassNameConverter sourceFileClassNameConverter) {
        return classes.stream()
            .flatMap(classToCompile -> sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile).stream())
            .filter(sourcePath -> sourcePath.endsWith(".java"))
            .flatMap(sourcePath -> sourceFileClassNameConverter.getClassNames(sourcePath).stream())
            .collect(Collectors.toSet());
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
