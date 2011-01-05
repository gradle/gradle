/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;

/**
 * A dumb incremental compiler. Deletes stale classes before invoking the actual compiler
 */
public abstract class IncrementalJavaSourceCompiler<T extends JavaSourceCompiler> implements JavaSourceCompiler {
    private final T compiler;
    private FileCollection source;
    private File destinationDir;

    public IncrementalJavaSourceCompiler(T compiler) {
        this.compiler = compiler;
    }

    public T getCompiler() {
        return compiler;
    }

    public CompileOptions getCompileOptions() {
        return compiler.getCompileOptions();
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        compiler.setSourceCompatibility(sourceCompatibility);
    }

    public void setTargetCompatibility(String targetCompatibility) {
        compiler.setTargetCompatibility(targetCompatibility);
    }

    public void setSource(FileCollection source) {
        this.source = source;
        compiler.setSource(source);
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
        compiler.setDestinationDir(destinationDir);
    }

    public void setClasspath(Iterable<File> classpath) {
        compiler.setClasspath(classpath);
    }

    public WorkResult execute() {
        StaleClassCleaner cleaner = createCleaner();
        cleaner.setDestinationDir(destinationDir);
        cleaner.setSource(source);
        cleaner.setCompileOptions(compiler.getCompileOptions());
        cleaner.execute();

        return compiler.execute();
    }

    protected abstract StaleClassCleaner createCleaner();
}
