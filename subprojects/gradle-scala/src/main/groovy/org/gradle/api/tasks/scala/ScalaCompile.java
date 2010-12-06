/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import org.gradle.api.AntBuilder;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.internal.tasks.scala.*;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
public class ScalaCompile extends AbstractCompile {
    private FileCollection scalaClasspath;

    private ScalaJavaJointCompiler compiler;

    public ScalaCompile() {
        ScalaCompiler scalaCompiler = new AntScalaCompiler(getServices().get(IsolatedAntBuilder.class));
        JavaCompiler javaCompiler = new AntJavaCompiler((Factory) getServices().getFactory(AntBuilder.class));
        compiler = new IncrementalScalaCompiler(new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler), getOutputs());
    }

    @InputFiles
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    public ScalaJavaJointCompiler getCompiler() {
        return compiler;
    }

    public void setCompiler(ScalaJavaJointCompiler compiler) {
        this.compiler = compiler;
    }

    @Nested
    public ScalaCompileOptions getScalaCompileOptions() {
        return compiler.getScalaCompileOptions();
    }

    @Nested
    public CompileOptions getOptions() {
        return compiler.getCompileOptions();
    }

    @Override
    protected void compile() {
        FileTree source = getSource();
        compiler.setSource(source);
        compiler.setDestinationDir(getDestinationDir());
        compiler.setClasspath(getClasspath());
        compiler.setScalaClasspath(getScalaClasspath());
        compiler.setSourceCompatibility(getSourceCompatibility());
        compiler.setTargetCompatibility(getTargetCompatibility());
        compiler.execute();
    }
}
