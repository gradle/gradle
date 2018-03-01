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
import org.gradle.api.internal.tasks.compile.reflect.SourcepathIgnoringProxy;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Factory;
import org.gradle.language.base.internal.compile.Compiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
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

    public JdkJavaCompiler(Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory) {
        this.javaHomeBasedJavaCompilerFactory = javaHomeBasedJavaCompilerFactory;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        LOGGER.info("Compiling with JDK Java compiler API.");

        JdkJavaCompilerResult result = new JdkJavaCompilerResult();
        JavaCompiler.CompilationTask task = createCompileTask(spec, result);
        boolean success = task.call();
        if (!success) {
            throw new CompilationFailedException();
        }
        return result;
    }

    private JavaCompiler.CompilationTask createCompileTask(JavaCompileSpec spec, JdkJavaCompilerResult result) {
        List<String> options = new JavaCompilerArgumentsBuilder(spec).build();
        JavaCompiler compiler = javaHomeBasedJavaCompilerFactory.create();
        MinimalJavaCompileOptions compileOptions = spec.getCompileOptions();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, compileOptions.getEncoding() != null ? Charset.forName(compileOptions.getEncoding()) : null);
        Iterable<? extends JavaFileObject> compilationUnits = standardFileManager.getJavaFileObjectsFromFiles(spec.getSource());
        StandardJavaFileManager fileManager = standardFileManager;
        if (JavaVersion.current().isJava9Compatible() && emptySourcepathIn(options)) {
            fileManager = (StandardJavaFileManager) SourcepathIgnoringProxy.proxy(standardFileManager, StandardJavaFileManager.class);
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);

        Set<AnnotationProcessorDeclaration> annotationProcessors = spec.getEffectiveAnnotationProcessors();
        if (annotationProcessors != null) {
            task = new IncrementalAnnotationProcessingCompileTask(task, annotationProcessors, spec.getAnnotationProcessorPath(), result.getAnnotationProcessingResult());
        }
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
