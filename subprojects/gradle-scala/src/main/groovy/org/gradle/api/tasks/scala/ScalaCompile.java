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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.project.AntBuilderFactory;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.internal.tasks.scala.AntScalaCompiler;
import org.gradle.api.internal.tasks.scala.ScalaCompiler;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Task to perform scala compilation.
 */
public class ScalaCompile extends Compile {
    private FileCollection scalaClasspath;

    private ScalaCompiler scalaCompiler = new AntScalaCompiler(getServices().get(IsolatedAntBuilder.class));

    public ScalaCompile() {
        setJavaCompiler(new AntJavaCompiler(getServices().get(AntBuilderFactory.class)));
    }

    public ScalaCompiler getScalaCompiler() {
        return scalaCompiler;
    }

    @InputFiles
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    public void setScalaCompiler(ScalaCompiler scalaCompiler) {
        this.scalaCompiler = scalaCompiler;
    }

    public ScalaCompileOptions getScalaCompileOptions() {
        return scalaCompiler.getScalaCompileOptions();
    }

    /**
     * Returns the Java source for this task.
     *
     * @return The Java source.
     */
    public FileTree getJavaSrc() {
        PatternFilterable patternSet = new PatternSet();
        patternSet.include("**/*.java");
        return getSource().matching(patternSet);
    }

    @Override
    protected void compile() {

        if (!GUtil.isTrue(getSourceCompatibility()) || !GUtil.isTrue(getTargetCompatibility())) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!");
        }

        FileTree source = getSource();
        scalaCompiler.setSource(source);
        scalaCompiler.setDestinationDir(getDestinationDir());
        scalaCompiler.setClasspath(getClasspath());
        scalaCompiler.setScalaClasspath(getScalaClasspath());
        scalaCompiler.execute();

        FileTree javaSource = getJavaSrc();
        List<File> classpath = GUtil.addLists(Collections.singleton(getDestinationDir()), getClasspath());
        javaSource.stopExecutionIfEmpty();
        JavaCompiler javaCompiler = getJavaCompiler();
        javaCompiler.setSource(javaSource);
        javaCompiler.setDestinationDir(getDestinationDir());
        javaCompiler.setDependencyCacheDir(getDependencyCacheDir());
        javaCompiler.setClasspath(classpath);
        javaCompiler.setSourceCompatibility(getSourceCompatibility());
        javaCompiler.setTargetCompatibility(getTargetCompatibility());
        javaCompiler.execute();
    }
}
