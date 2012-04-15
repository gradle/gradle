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

        // The most accurate class loader setup would be the following:
        // 1. One class loader for compile dependencies that loads everything on spec.getClasspath()
        // 2. Another class loader for AST transforms that delegates loading of compiler classes to
        // getClass().getClassLoader(), and delegates everything else to 1.
        //
        // However, JavaAwareCompilationUnit doesn't provide a way to set a separate class loader for transforms
        // like CompilationUnit does. One cannot even set CompilationUnit.transformLoader reflectively because it's
        // already used in the constructor. Therefore, we pass a single class loader to the Groovy compiler that's
        // used both for the compile class path and AST transforms. This class loader is a combination of 1. and 2. above.

        // The purpose of groovyCompilerClassLoader is to share Groovy compiler classes
        // between the compiler and AST transforms. This is required for AST transforms to work correctly.
        FilteringClassLoader groovyCompilerClassLoader = new FilteringClassLoader(getClass().getClassLoader());
        groovyCompilerClassLoader.allowPackage("org.codehaus.groovy");

        // As we found out the hard way, the following allowances will lead to problems:

        // compiling code that makes use of GroovyTestCase (more generally, code that makes use of a Groovy class
        // that depends on a class that's not on the 'groovy' configuration) leads to a NoClassDefFoundError in the compiler
        // groovyCompilerClassLoader.allowPackage("groovy");

        // compiler finds some global transform descriptors (e.g. Spock) whose corresponding
        // transform implementation classes it fails to load
        //groovyCompilerClassLoader.allowResources("META-INF/services");

        // Necessary for Groovy compilation to pick up output of regular and joint Java compilation,
        // and for joint Java compilation to pick up the output of regular Java compilation.
        // Assumes that output of regular Java compilation (which is not under this task's control) also goes
        // into spec.getDestinationDir(). We could configure this on source set level, but then spec.getDestinationDir()
        // would end up on the compile class path of every compile task for that source set, which may not be desirable.
        spec.setClasspath(Iterables.concat(spec.getClasspath(), Collections.singleton(spec.getDestinationDir())));

        GroovyClassLoader compilationUnitClassLoader = new GroovyClassLoader(groovyCompilerClassLoader, null);
        for (File file : spec.getClasspath()) {
            compilationUnitClassLoader.addClasspath(file.getPath());
        }

        JavaAwareCompilationUnit unit = new JavaAwareCompilationUnit(configuration, compilationUnitClassLoader);
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
