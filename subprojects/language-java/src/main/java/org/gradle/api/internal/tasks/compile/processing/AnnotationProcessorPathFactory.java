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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class AnnotationProcessorPathFactory {
    public static final String COMPILE_CLASSPATH_DEPRECATION_MESSAGE = "The following annotation processors were detected on the compile classpath:";
    public static final String PROCESSOR_PATH_DEPRECATION_MESSAGE = "Specifying the processor path in the CompilerOptions compilerArgs property";

    private final FileCollectionFactory fileCollectionFactory;
    private final AnnotationProcessorDetector annotationProcessorDetector;

    public AnnotationProcessorPathFactory(FileCollectionFactory fileCollectionFactory, AnnotationProcessorDetector annotationProcessorDetector) {
        this.fileCollectionFactory = fileCollectionFactory;
        this.annotationProcessorDetector = annotationProcessorDetector;
    }

    /**
     * Calculates the annotation processor path to use given some compile options and compile classpath.
     *
     * For backwards compatibility we still support the -processorpath option and we also look for processors
     * on the compile classpath if the processor path was an empty {@link DefaultProcessorPath}. In Gradle 5.0 we will ignore
     * -processorpath and the compile classpath. We will then use the annotationProcessorPath as the single source of truth.
     *
     * @return An empty collection when annotation processing should not be performed, non-empty when it should.
     */
    public FileCollection getEffectiveAnnotationProcessorClasspath(final CompileOptions compileOptions, final FileCollection compileClasspath) {
        if (compileOptions.getAllCompilerArgs().contains("-proc:none")) {
            return fileCollectionFactory.empty("annotation processor path");
        }
        final FileCollection annotationProcessorPath = compileOptions.getAnnotationProcessorPath();
        if (annotationProcessorPath != null && !(annotationProcessorPath instanceof DefaultProcessorPath)) {
            return annotationProcessorPath;
        }
        FileCollection processorPathFromCompilerArguments = getProcessorPathFromCompilerArguments(compileOptions);
        if (processorPathFromCompilerArguments != null) {
            return processorPathFromCompilerArguments;
        }
        if (compileClasspath == null) {
            return annotationProcessorPath;
        }
        return getProcessorPathWithCompileClasspathFallback(compileOptions, compileClasspath, annotationProcessorPath);
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
        DeprecationLogger.nagUserOfDeprecated(
            PROCESSOR_PATH_DEPRECATION_MESSAGE,
            "Instead, use the CompilerOptions.annotationProcessorPath property directly");
        LinkedHashSet<File> files = new LinkedHashSet<File>();
        for (String path : Splitter.on(File.pathSeparatorChar).splitToList(processorpath)) {
            files.add(new File(path));
        }
        return files;
    }

    private FileCollection getProcessorPathWithCompileClasspathFallback(CompileOptions compileOptions, final FileCollection compileClasspath, final FileCollection annotationProcessorPath) {
        final boolean hasExplicitProcessor = checkExplicitProcessorOption(compileOptions);
        return fileCollectionFactory.create(
            new AbstractTaskDependency() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    if (annotationProcessorPath != null) {
                        context.add(annotationProcessorPath);
                    }
                    context.add(compileClasspath);
                }
            },
            new MinimalFileSet() {
                @Override
                public Set<File> getFiles() {
                    if (annotationProcessorPath != null && !annotationProcessorPath.isEmpty()) {
                        return annotationProcessorPath.getFiles();
                    }
                    if (hasExplicitProcessor) {
                        return compileClasspath.getFiles();
                    }
                    Map<String, AnnotationProcessorDeclaration> processors = annotationProcessorDetector.detectProcessors(compileClasspath);
                    if (!processors.isEmpty()) {
                        DeprecationLogger.nagUserWith(
                            COMPILE_CLASSPATH_DEPRECATION_MESSAGE +
                            " '" + Joiner.on("' and '").join(processors.keySet()) + "'. " +
                            "Detecting annotation processors on the compile classpath is deprecated.",
                            "Gradle 5.0 will ignore annotation processors on the compile classpath.",
                            "Please add them to the annotation processor path instead. " +
                            "If you did not intend to use annotation processors, you can use the '-proc:none' compiler argument to ignore them."
                        );
                        return compileClasspath.getFiles();
                    }
                    return Collections.emptySet();
                }

                @Override
                public final String getDisplayName() {
                    return "annotation processor path";
                }
            });
    }

    private static boolean checkExplicitProcessorOption(CompileOptions compileOptions) {
        boolean hasExplicitProcessor = false;
        List<String> compilerArgs = compileOptions.getAllCompilerArgs();
        int pos = compilerArgs.indexOf("-processor");
        if (pos >= 0) {
            if (pos == compilerArgs.size() - 1) {
                throw new InvalidUserDataException("No processor specified for compiler argument -processor in requested compiler args: " + Joiner.on(" ").join(compilerArgs));
            }
            hasExplicitProcessor = true;
        }
        return hasExplicitProcessor;
    }
}
