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
import org.gradle.api.file.FileTree;
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

    private AntScalaCompile antScalaCompile;

    private ScalaCompileOptions scalaCompileOptions = new ScalaCompileOptions();

    public AntScalaCompile getAntScalaCompile() {
        if (antScalaCompile == null) {
            antScalaCompile = new AntScalaCompile(getAnt());
        }
        return antScalaCompile;
    }

    public void setAntScalaCompile(AntScalaCompile antScalaCompile) {
        this.antScalaCompile = antScalaCompile;
    }

    public ScalaCompileOptions getScalaCompileOptions() {
        return scalaCompileOptions;
    }

    public void setScalaCompileOptions(ScalaCompileOptions scalaCompileOptions) {
        this.scalaCompileOptions = scalaCompileOptions;
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

        if (!GUtil.isTrue(getTargetCompatibility())) {
            throw new InvalidUserDataException("The targetCompatibility must be set!");
        }

        FileTree source = getSource();
        getAntScalaCompile().execute(source, getDestinationDir(), getClasspath(), getScalaCompileOptions());

        FileTree javaSource = getJavaSrc();
        List<File> classpath = GUtil.addLists(Collections.singleton(getDestinationDir()), getClasspath());
        javaSource.stopExecutionIfEmpty();
        antCompile.execute(javaSource, getDestinationDir(), getDependencyCacheDir(), classpath,
                getSourceCompatibility(), getTargetCompatibility(), getOptions(), getAnt());
    }
}
