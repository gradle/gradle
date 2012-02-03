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

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.io.Serializable;

/**
 * Convenience base class for implementing <tt>JavaCompiler</tt>s that keeps
 * all required state in protected fields.
 */
public abstract class JavaCompilerSupport implements JavaCompiler, Serializable {
    protected String sourceCompatibility;
    protected String targetCompatibility;
    protected File destinationDir;
    protected Iterable<File> classpath;
    protected FileCollection source;
    protected File dependencyCacheDir;
    protected CompileOptions compileOptions = new CompileOptions();

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public void setClasspath(Iterable<File> classpath) {
        this.classpath = classpath;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    public CompileOptions getCompileOptions() {
        return compileOptions;
    }

    public void setCompileOptions(CompileOptions compileOptions) {
        this.compileOptions = compileOptions;
    }
    
    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }
    
    public void configure(JavaCompiler other) {
        other.setSource(source);
        other.setDestinationDir(destinationDir);
        other.setClasspath(classpath);
        other.setSourceCompatibility(sourceCompatibility);
        other.setTargetCompatibility(targetCompatibility);
        other.setDependencyCacheDir(dependencyCacheDir);
        other.setCompileOptions(compileOptions);
    }

    protected void listFilesIfRequested() {
        if (!compileOptions.isListFiles()) { return; }
        
        StringBuilder builder = new StringBuilder();
        builder.append("Source files to be compiled:");
        for (File file : source) {
            builder.append('\n');
            builder.append(file);
        }
        System.out.println(builder.toString());
    }
}
