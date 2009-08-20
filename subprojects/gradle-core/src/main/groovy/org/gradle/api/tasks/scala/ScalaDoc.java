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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Task to perform scala compilation.
 */
public class ScalaDoc extends ConventionTask {

    /**
     * The directory to put the generated documentation.
     */
    private File destinationDir;

    /**
     * Directories containing input scala source files.
     */
    private List<File> scalaSrcDirs;

    private PatternFilterable scalaPatternSet = new PatternSet();

    private FileCollection classpath;
    private AntScalaDoc antScalaDoc;
    private ScalaDocOptions scalaDocOptions = new ScalaDocOptions();
    protected ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    public ScalaDoc(Project project, String name) {
        super(project, name);
        setActions(new ArrayList<TaskAction>());
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
    }

    public AntScalaDoc getAntScalaDoc() {
        if (antScalaDoc == null) {
            antScalaDoc = new AntScalaDoc(getAnt());
        }
        return antScalaDoc;
    }

    public void setAntScalaDoc(AntScalaDoc antScalaDoc) {
        this.antScalaDoc = antScalaDoc;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public List<File> getScalaSrcDirs() {
        return scalaSrcDirs;
    }

    public void setScalaSrcDirs(List<File> scalaSrcDirs) {
        this.scalaSrcDirs = scalaSrcDirs;
    }

    /**
     * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
     *
     * @return The classpath.
     */
    public Iterable<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public Set<String> getScalaIncludes() {
        return scalaPatternSet.getIncludes();
    }

    public void setScalaIncludes(Iterable<String> scalaIncludes) {
        scalaPatternSet.setIncludes(scalaIncludes);
    }

    public ScalaDoc scalaInclude(String... includes) {
        scalaPatternSet.include(includes);
        return this;
    }

    public ScalaDoc scalaInclude(Iterable<String> includes) {
        scalaPatternSet.include(includes);
        return this;
    }

    public Set<String> getScalaExcludes() {
        return scalaPatternSet.getExcludes();
    }

    public void setScalaExcludes(List<String> scalaExcludes) {
        scalaPatternSet.setExcludes(scalaExcludes);
    }

    public ScalaDoc scalaExclude(String... excludes) {
        scalaPatternSet.exclude(excludes);
        return this;
    }

    public ScalaDoc scalaExclude(Iterable<String> excludes) {
        scalaPatternSet.exclude(excludes);
        return this;
    }

    public ScalaDocOptions getScalaDocOptions() {
        return scalaDocOptions;
    }

    public void setScalaDocOptions(ScalaDocOptions scalaDocOptions) {
        this.scalaDocOptions = scalaDocOptions;
    }

    protected void generate() {

        List<File> existingSrcDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getScalaSrcDirs());

        getAntScalaDoc().execute(existingSrcDirs, getScalaIncludes(), getScalaExcludes(), getDestinationDir(),
                getClasspath(), getScalaDocOptions());
    }

}
