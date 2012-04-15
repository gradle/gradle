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

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.tools.javac.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Copy of Groovy's org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit that provides a constructor to set CompilationUnit's transformLoader field.
 * Requires Groovy 1.6 or higher. (Older Groovy versions don't have AST transforms and hence don't have a transformLoader.)
 */
public class JavaAwareGroovyCompilationUnit extends CompilationUnit {
    private List<String> javaSources;
    private JavaStubGenerator stubGenerator;
    private org.codehaus.groovy.tools.javac.JavaCompilerFactory compilerFactory = new JavacCompilerFactory();
    private File generationGoal;
    private boolean keepStubs;

    public JavaAwareGroovyCompilationUnit(CompilerConfiguration configuration, GroovyClassLoader groovyClassLoader, GroovyClassLoader transformClassLoader) {
        super(configuration, null, groovyClassLoader, transformClassLoader);
        javaSources = new LinkedList<String>();
        Map options = configuration.getJointCompilationOptions();
        generationGoal = (File) options.get("stubDir");
        boolean useJava5 = configuration.getTargetBytecode().equals(CompilerConfiguration.POST_JDK5);
        stubGenerator = new JavaStubGenerator(generationGoal, false, useJava5);
        keepStubs = Boolean.TRUE.equals(options.get("keepStubs"));

        addPhaseOperation(new PrimaryClassNodeOperation() {
            public void call(SourceUnit source, GeneratorContext context, ClassNode node) throws org.codehaus.groovy.control.CompilationFailedException {
                if (javaSources.size() != 0) {
                    VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(source);
                    scopeVisitor.visitClass(node);
                    new JavaAwareResolveVisitor(JavaAwareGroovyCompilationUnit.this).startResolving(node, source);
                }
            }
        }, Phases.CONVERSION);

        addPhaseOperation(new PrimaryClassNodeOperation() {
            public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws org.codehaus.groovy.control.CompilationFailedException {
                try {
                    if (javaSources.size() != 0) {
                        stubGenerator.generateClass(classNode);
                    }
                } catch (FileNotFoundException fnfe) {
                    source.addException(fnfe);
                }
            }
        }, Phases.CONVERSION);
    }

    public void gotoPhase(int phase) throws org.codehaus.groovy.control.CompilationFailedException {
        super.gotoPhase(phase);
        // compile Java and clean up
        if (phase == Phases.SEMANTIC_ANALYSIS && javaSources.size() > 0) {
            for (ModuleNode module : getAST().getModules()) {
                module.setImportsResolved(false);
            }
            try {
                JavaCompiler compiler = compilerFactory.createCompiler(getConfiguration());
                compiler.compile(javaSources, this);
            } finally {
                if (!keepStubs) {
                    stubGenerator.clean();
                }
                javaSources.clear();
            }
        }
    }

    public void configure(CompilerConfiguration configuration) {
        super.configure(configuration);
        // GroovyClassLoader should be able to find classes compiled from java
        // sources
        File targetDir = configuration.getTargetDirectory();
        if (targetDir != null) {
            final String classOutput = targetDir.getAbsolutePath();
            getClassLoader().addClasspath(classOutput);
        }
    }

    private void addJavaSource(File file) {
        String path = file.getAbsolutePath();
        for (String source : javaSources) {
            if (path.equals(source)) {
                return;
            }
        }
        javaSources.add(path);
    }

    public void addSources(String[] paths) {
        for (String path : paths) {
            File file = new File(path);
            if (file.getName().endsWith(".java")) {
                addJavaSource(file);
            } else {
                addSource(file);
            }
        }
    }

    public void addSources(File[] files) {
        for (File file : files) {
            if (file.getName().endsWith(".java")) {
                addJavaSource(file);
            } else {
                addSource(file);
            }
        }
    }

    public org.codehaus.groovy.tools.javac.JavaCompilerFactory getCompilerFactory() {
        return compilerFactory;
    }

    public void setCompilerFactory(org.codehaus.groovy.tools.javac.JavaCompilerFactory compilerFactory) {
        this.compilerFactory = compilerFactory;
    }
}
