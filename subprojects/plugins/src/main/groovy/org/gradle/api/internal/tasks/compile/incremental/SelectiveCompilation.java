/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.Clock;

import java.io.File;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 1/16/14
 */
public class SelectiveCompilation {
    private final FileCollection source;
    private final FileCollection classpath;
    private File classDeltaCache;
    private SelectiveJavaCompiler compiler;
    private static final Logger LOG = Logging.getLogger(SelectiveCompilation.class);
    private String rebuildNeeded;
    private boolean compilationNeeded = true;

    public SelectiveCompilation(IncrementalTaskInputs inputs, FileTree source, FileCollection compileClasspath, final File compileDestination,
                                final File classTreeCache, final File classDeltaCache, final SelectiveJavaCompiler compiler, Iterable<File> sourceDirs) {
        this.classDeltaCache = classDeltaCache;
        this.compiler = compiler;

        Clock clock = new Clock();
        final InputOutputMapper mapper = new InputOutputMapper(sourceDirs, compileDestination);

        //load dependency tree
        final ClassDependencyTree tree = ClassDependencyTree.loadFrom(classTreeCache);

        //including only source java classes that were changed
        final PatternSet changedSourceOnly = new PatternSet();
        inputs.outOfDate(new Action<InputFileDetails>() {
            public void execute(InputFileDetails inputFileDetails) {
                if (rebuildNeeded != null) {
                    return;
                }
                File inputFile = inputFileDetails.getFile();
                String name = inputFile.getName();
                if (name.endsWith(".java")) {
                    JavaSourceClass source = mapper.toJavaSourceClass(inputFile);
                    compiler.addStaleClass(source);
                    changedSourceOnly.include(source.getRelativePath());
                    Set<String> actualDependents = tree.getActualDependents(source.getClassName());
                    if (actualDependents == null) {
                        rebuildNeeded = "change to " + source.getClassName() + " requires full rebuild";
                        return;
                    }
                    for (String d : actualDependents) {
                        JavaSourceClass dSource = mapper.toJavaSourceClass(d);
                        compiler.addStaleClass(dSource);
                        changedSourceOnly.include(dSource.getRelativePath());
                    }
                }
                if (name.endsWith(".jar")) {
                    JarDeltaProvider delta = new JarDeltaProvider(inputFile);
                    if (delta.isRebuildNeeded()) {
                        //for example, a source annotation in the dependency jar has changed
                        //or it's a change in a 3rd party jar
                        rebuildNeeded = "change to " + inputFile + " requires full rebuild";
                        return;
                    }
                    Iterable<String> classes = delta.getChangedClasses();
                    for (String c : classes) {
                        Set<String> actualDependents = tree.getActualDependents(c);
                        for (String d : actualDependents) {
                            JavaSourceClass dSource = mapper.toJavaSourceClass(d);
                            compiler.addStaleClass(dSource);
                            changedSourceOnly.include(dSource.getRelativePath());
                        }
                    }
                }
            }
        });
        if (rebuildNeeded != null) {
            LOG.lifecycle("Stale classes detection completed in {}. Rebuild needed: {}.", clock.getTime(), rebuildNeeded);
            this.classpath = compileClasspath;
            this.source = source;
            return;
        }
        inputs.removed(new Action<InputFileDetails>() {
            public void execute(InputFileDetails inputFileDetails) {
                compiler.addStaleClass(mapper.toJavaSourceClass(inputFileDetails.getFile()));
                //TODO needs to schedule for recompilation all dependencies of this class
            }
        });
        //since we're compiling selectively we need to include the classes compiled previously
        if (changedSourceOnly.getIncludes().isEmpty()) {
            this.compilationNeeded = false;
            this.classpath = compileClasspath;
            this.source = source;
        } else {
            this.classpath = compileClasspath.plus(new SimpleFileCollection(compileDestination));
            this.source = source.matching(changedSourceOnly);
        }
        LOG.lifecycle("Stale classes detection completed in {}. Stale classes: {}, Compile include patterns: {}, Files to delete: {}", clock.getTime(), compiler.getStaleClasses().size(), changedSourceOnly.getIncludes(), compiler.getStaleClasses());
    }

    public void compilationComplete() {
        if (rebuildNeeded == null) {
            classDeltaCache.getParentFile().mkdirs();
            DummySerializer.writeTargetTo(classDeltaCache, compiler.getChangedSources());
        }
    }

    public FileCollection getSource() {
        return source;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public boolean getCompilationNeeded() {
        return compilationNeeded;
    }
}
