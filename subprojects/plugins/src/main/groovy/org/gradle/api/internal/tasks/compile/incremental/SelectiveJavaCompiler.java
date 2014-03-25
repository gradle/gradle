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

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.Clock;

import java.io.File;
import java.util.Set;

public class SelectiveJavaCompiler implements Compiler<JavaCompileSpec> {
    private Compiler<JavaCompileSpec> compiler;
    private final FileTree destinationDir;
    private final PatternSet staleClasses = new PatternSet();
    private final static Logger LOG = Logging.getLogger(SelectiveJavaCompiler.class);

    public SelectiveJavaCompiler(Compiler<JavaCompileSpec> compiler, FileTree destinationDir) {
        this.compiler = compiler;
        this.destinationDir = destinationDir;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        return compiler.execute(spec);
    }

    void deleteStaleClasses() {
        Clock clock = new Clock();
        Set<File> files = destinationDir.matching(staleClasses).getFiles();
        for (File file : files) {
            file.delete();
        }
        LOG.lifecycle("Deleting {} stale classes took {}", files.size(), clock.getTime());
    }

    public void addStaleClass(JavaSourceClass source) {
        addStaleClass(source.getClassName());
    }

    public void addStaleClass(String className) {
        String path = className.replaceAll("\\.", "/");
        String cls = path.concat(".class");
        String inner = path.concat("$*.class");
        staleClasses.include(cls);
        staleClasses.include(inner);
    }
}