/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.Iterables;

import groovy.lang.GroovyClassLoader;

import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.tools.javac.*;

import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.FilteringClassLoader;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiGroovyCompiler implements Compiler<GroovyJavaJointCompileSpec>, Serializable {
    private final Compiler<JavaCompileSpec> javaCompiler;

    public ApiGroovyCompiler(Compiler<JavaCompileSpec> javaCompiler) {
        this.javaCompiler = javaCompiler;
    }

    public WorkResult execute(final GroovyJavaJointCompileSpec spec) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setVerbose(spec.getGroovyCompileOptions().isVerbose());
        configuration.setSourceEncoding(spec.getGroovyCompileOptions().getEncoding());
        configuration.setTargetBytecode(spec.getTargetCompatibility());
        configuration.setTargetDirectory(spec.getDestinationDir());
        Map<String, Object> jointCompilationOptions = new HashMap<String, Object>();
        jointCompilationOptions.put("stubDir", spec.getGroovyCompileOptions().getStubDir());
        jointCompilationOptions.put("keepStubs", spec.getGroovyCompileOptions().isKeepStubs());
        configuration.setJointCompilationOptions(jointCompilationOptions);

        // Necessary for Groovy compilation to pick up output of regular and joint Java compilation,
        // and for joint Java compilation to pick up the output of regular Java compilation.
        // Assumes that output of regular Java compilation (which is not under this task's control) also goes
        // into spec.getDestinationDir(). We could configure this on source set level, but then spec.getDestinationDir()
        // would end up on the compile class path of every compile task for that source set, which may not be desirable.
        spec.setClasspath(Iterables.concat(spec.getClasspath(), Collections.singleton(spec.getDestinationDir())));

        GroovyClassLoader compileClasspathClassLoader = new GroovyClassLoader(null, null);
        for (File file : spec.getClasspath()) {
            compileClasspathClassLoader.addClasspath(file.getPath());
        }

        FilteringClassLoader groovyCompilerClassLoader = new FilteringClassLoader(getClass().getClassLoader());
        groovyCompilerClassLoader.allowPackage("org.codehaus.groovy");
        groovyCompilerClassLoader.allowPackage("groovy");

        // AST transforms need their own class loader that shares compiler classes with the compiler itself
        final GroovyClassLoader astTransformClassLoader = new GroovyClassLoader(groovyCompilerClassLoader, null);
        // can't delegate to compileClasspathLoader because this would result in ASTTransformation interface
        // (which is implemented by the transform class) being loaded by compileClasspathClassLoader (which is
        // where the transform class is loaded from)
        for (File file : spec.getClasspath()) {
            astTransformClassLoader.addClasspath(file.getPath());
        }

        JavaAwareCompilationUnit unit = new JavaAwareCompilationUnit(configuration, compileClasspathClassLoader) {
            @Override
            public GroovyClassLoader getTransformLoader() {
                return astTransformClassLoader;
            }
        };
        unit.addSources(Iterables.toArray(spec.getSource(), File.class));
        unit.setCompilerFactory(new org.codehaus.groovy.tools.javac.JavaCompilerFactory() {
            public JavaCompiler createCompiler(final CompilerConfiguration config) {
                return new JavaCompiler() {
                    public void compile(List<String> files, CompilationUnit cu) {
                        spec.setSource(spec.getSource().filter(new Spec<File>() {
                            public boolean isSatisfiedBy(File file) {
                                return file.getName().endsWith(".java");
                            }
                        }));
                        spec.getCompileOptions().getCompilerArgs().add("-sourcepath");
                        spec.getCompileOptions().getCompilerArgs().add(((File) config.getJointCompilationOptions().get("stubDir")).getAbsolutePath());
                        try {
                            javaCompiler.execute(spec);
                        } catch (CompilationFailedException e) {
                            cu.getErrorCollector().addFatalError(new SimpleMessage(e.getMessage(), cu));
                        }
                    }
                };
            }
        });

        try {
            unit.compile();
        } catch (org.codehaus.groovy.control.CompilationFailedException e) {
            throw new CompilationFailedException(e.getMessage());
        }

        return new SimpleWorkResult(true);
    }
}
