/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.internal.tasks.compile.reflect.GradleStandardJavaFileManager;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class JdkJavaCompiler implements Compiler<JavaCompileSpec>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkJavaCompiler.class);

    private final Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory;
    private final DiagnosticToProblemListener diagnosticToProblemListener;

    @Inject
    public JdkJavaCompiler(Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory, Problems problems) {
        this.javaHomeBasedJavaCompilerFactory = javaHomeBasedJavaCompilerFactory;
        this.diagnosticToProblemListener = new DiagnosticToProblemListener(problems);
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        LOGGER.info("Compiling with JDK Java compiler API.");

        ApiCompilerResult result = new ApiCompilerResult();
        JavaCompiler.CompilationTask task = createCompileTask(spec, result, diagnosticToProblemListener);
        boolean success = task.call();
        if (!success) {
            throw new CompilationFailedException(result);
        }
        return result;
    }

    private JavaCompiler.CompilationTask createCompileTask(JavaCompileSpec spec, ApiCompilerResult result, DiagnosticListener<JavaFileObject> diagnosticListener) {
        List<String> options = new JavaCompilerArgumentsBuilder(spec).build();
        JavaCompiler compiler = javaHomeBasedJavaCompilerFactory.create();
        MinimalJavaCompileOptions compileOptions = spec.getCompileOptions();
        Charset charset = compileOptions.getEncoding() != null ? Charset.forName(compileOptions.getEncoding()) : null;
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, charset);
        Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromFiles(spec.getSourceFiles());
        boolean hasEmptySourcepaths = JavaVersion.current().isJava9Compatible() && emptySourcepathIn(options);
        JavaFileManager fileManager = GradleStandardJavaFileManager.wrap(standardFileManager, DefaultClassPath.of(spec.getAnnotationProcessorPath()), hasEmptySourcepaths);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, spec.getClassesToProcess(), compilationUnits);
        if (compiler instanceof IncrementalCompilationAwareJavaCompiler) {
            task = ((IncrementalCompilationAwareJavaCompiler) compiler).makeIncremental(
                task,
                result.getSourceClassesMapping(),
                result.getConstantsAnalysisResult(),
                new CompilationSourceDirs(spec),
                new CompilationClassBackupService(spec, result)
            );
        }
        Set<AnnotationProcessorDeclaration> annotationProcessors = spec.getEffectiveAnnotationProcessors();
        task = new AnnotationProcessingCompileTask(task, annotationProcessors, spec.getAnnotationProcessorPath(), result.getAnnotationProcessingResult());
        task = new ResourceCleaningCompilationTask(task, fileManager);
        return task;
    }

    private static boolean emptySourcepathIn(List<String> options) {
        Iterator<String> optionsIter = options.iterator();
        while (optionsIter.hasNext()) {
            String current = optionsIter.next();
            if (current.equals("-sourcepath") || current.equals("--source-path")) {
                return optionsIter.next().isEmpty();
            }
        }
        return false;
    }
}
