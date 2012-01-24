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
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;

public class SwitchableJavaCompiler implements JavaCompiler {
    private final CompilerChooser compilerChooser;
    
    private FileCollection source;
    private File destinationDir;
    private Iterable<File> classpath;
    private String sourceCompatibility;
    private String targetCompatibility;
    private CompileOptions compileOptions = new CompileOptions();

    public SwitchableJavaCompiler(CompilerChooser compilerChooser) {
        this.compilerChooser = compilerChooser;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public void setClasspath(Iterable<File> classpath) {
        this.classpath = classpath;
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public CompileOptions getCompileOptions() {
        return compileOptions;
    }

    public void setCompileOptions(CompileOptions compileOptions) {
        this.compileOptions = compileOptions;
    }

    public void setDependencyCacheDir(File dir) {
        // do nothing
    }

    public WorkResult execute() {
        JavaCompiler actualCompiler = compilerChooser.choose(compileOptions);
        configure(actualCompiler);
        return actualCompiler.execute();
    }
    
    private void configure(JavaCompiler compiler) {
        compiler.setSource(source);
        compiler.setDestinationDir(destinationDir);
        compiler.setClasspath(classpath);
        compiler.setSourceCompatibility(sourceCompatibility);
        compiler.setTargetCompatibility(targetCompatibility);
        compiler.setCompileOptions(compileOptions);
    }
}
