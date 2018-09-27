/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class AnnotationProcessorPathFactory {
    public static final String PROCESSOR_PATH_DEPRECATION_MESSAGE = "Specifying the processor path in the CompilerOptions compilerArgs property";

    private final FileCollectionFactory fileCollectionFactory;

    public AnnotationProcessorPathFactory(FileCollectionFactory fileCollectionFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
    }

    /**
     * Calculates the annotation processor path to use given some compile options and compile classpath.
     *
     * For backwards compatibility we still support the -processorpath option if the processor path was an empty {@link DefaultProcessorPath}.
     * In Gradle 5.0 we will ignore -processorpath. We will then use the annotationProcessorPath as the single source of truth.
     *
     * @return An empty collection when annotation processing should not be performed, non-empty when it should.
     */
    public FileCollection getEffectiveAnnotationProcessorClasspath(final CompileOptions compileOptions) {
        if (compileOptions.getAllCompilerArgs().contains("-proc:none")) {
            return emptyAnnotationProcessorPath();
        }
        final FileCollection annotationProcessorPath = compileOptions.getAnnotationProcessorPath();
        if (annotationProcessorPath != null && !(annotationProcessorPath instanceof DefaultProcessorPath)) {
            return annotationProcessorPath;
        }
        FileCollection processorPathFromCompilerArguments = getProcessorPathFromCompilerArguments(compileOptions);
        if (processorPathFromCompilerArguments != null) {
            return processorPathFromCompilerArguments;
        }
        return annotationProcessorPath == null ? emptyAnnotationProcessorPath() : annotationProcessorPath;
    }

    private FileCollection getProcessorPathFromCompilerArguments(final CompileOptions compileOptions) {
        final FileCollection annotationProcessorPath = compileOptions.getAnnotationProcessorPath();
        List<String> compilerArgs = compileOptions.getAllCompilerArgs();
        int pos = compilerArgs.indexOf("-processorpath");
        if (pos < 0) {
            return null;
        }
        if (pos == compilerArgs.size() - 1) {
            throw new InvalidUserDataException("No path provided for compiler argument -processorpath in requested compiler args: " + Joiner.on(" ").join(compilerArgs));
        }
        final String processorpath = compilerArgs.get(pos + 1);
        if (annotationProcessorPath == null) {
            return fileCollectionFactory.fixed("annotation processor path", extractProcessorPath(processorpath));
        }
        return fileCollectionFactory.create(
            new AbstractTaskDependency() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    context.add(annotationProcessorPath);
                }
            },
            new MinimalFileSet() {
                @Override
                public Set<File> getFiles() {
                    if (!annotationProcessorPath.isEmpty()) {
                        return annotationProcessorPath.getFiles();
                    }
                    return extractProcessorPath(processorpath);
                }

                @Override
                public final String getDisplayName() {
                    return "annotation processor path";
                }
            });
    }

    private static Set<File> extractProcessorPath(String processorpath) {
        DeprecationLogger.nagUserWithDeprecatedIndirectUserCodeCause(
            PROCESSOR_PATH_DEPRECATION_MESSAGE,
            "Instead, use the CompilerOptions.annotationProcessorPath property directly");
        LinkedHashSet<File> files = new LinkedHashSet<File>();
        for (String path : Splitter.on(File.pathSeparatorChar).splitToList(processorpath)) {
            files.add(new File(path));
        }
        return files;
    }

    private FileCollection emptyAnnotationProcessorPath() {
        return fileCollectionFactory.empty("annotation processor path");
    }
}
