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
package org.gradle.api.internal.tasks.scala;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

public class DefaultScalaJavaJointCompiler implements ScalaJavaJointCompiler {
    private final ScalaCompiler scalaCompiler;
    private final JavaCompiler javaCompiler;
    private FileCollection source;

    public DefaultScalaJavaJointCompiler(ScalaCompiler scalaCompiler, JavaCompiler javaCompiler) {
        this.scalaCompiler = scalaCompiler;
        this.javaCompiler = javaCompiler;
    }

    public ScalaCompileOptions getScalaCompileOptions() {
        return scalaCompiler.getScalaCompileOptions();
    }

    public void setScalaClasspath(Iterable<File> classpath) {
        scalaCompiler.setScalaClasspath(classpath);
    }

    public void setSource(FileCollection source) {
        this.source = source;
        scalaCompiler.setSource(source);
    }

    public void setDestinationDir(File destinationDir) {
        scalaCompiler.setDestinationDir(destinationDir);
        javaCompiler.setDestinationDir(destinationDir);
    }

    public void setClasspath(Iterable<File> classpath) {
        scalaCompiler.setClasspath(classpath);
        javaCompiler.setClasspath(classpath);
    }

    public CompileOptions getCompileOptions() {
        return javaCompiler.getCompileOptions();
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        javaCompiler.setSourceCompatibility(sourceCompatibility);
    }

    public void setTargetCompatibility(String targetCompatibility) {
        javaCompiler.setTargetCompatibility(targetCompatibility);
    }

    public WorkResult execute() {
        scalaCompiler.execute();

        PatternFilterable patternSet = new PatternSet();
        patternSet.include("**/*.java");
        FileTree javaSource = source.getAsFileTree().matching(patternSet);
        if (!javaSource.isEmpty()) {
            javaCompiler.setSource(javaSource);
            javaCompiler.execute();
        }

        return new WorkResult() {
            public boolean getDidWork() {
                return true;
            }
        };
    }
}
