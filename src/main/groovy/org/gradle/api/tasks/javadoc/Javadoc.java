/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.javadoc;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.GradleException;
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.execution.Dag;
import org.gradle.util.GUtil;
import org.apache.tools.ant.BuildException;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * <p>Generates Javadoc from a number of java source directories.</p>
 *
 * @author Hans Dockter
 */
public class Javadoc extends ConventionTask {
    private List<File> srcDirs;

    private File destinationDir;

    private DependencyManager dependencyManager;

    private String title;

    private String maxMemory;

    private List<String> includes = new ArrayList<String>();

    private List<String> excludes = new ArrayList<String>();

    private ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    private AntJavadoc antJavadoc = new AntJavadoc();

    public Javadoc(Project project, String name, Dag tasksGraph) {
        super(project, name, tasksGraph);
        doFirst(new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                generate();
            }
        });
    }

    private void generate() {
        List<File> existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(getDestinationDir(), getSrcDirs());
        try {
            antJavadoc.execute(existingSourceDirs, getDestinationDir(), getClasspath(), getTitle(), getMaxMemory(), getIncludes(), getExcludes(), getProject().getAnt());
        } catch (BuildException e) {
            throw new GradleException("Javadoc generation failed.", e);
        }
    }

    /**
     * <p>Returns the source directories containing the java source files to generate documentation for.</p>
     *
     * @return The source directories. Never returns null.
     */
    public List<File> getSrcDirs() {
        return (List<File>) conv(srcDirs, "srcDirs");
    }

    /**
     * <p>Sets the source directories containing the java source files to generate documentation for.</p>
     */
    public void setSrcDirs(List<File> srcDirs) {
        this.srcDirs = new ArrayList<File>(srcDirs);
    }

    /**
     * <p>Returns the directory to generate the documentation into.</p>
     *
     * @return The directory.
     */
    public File getDestinationDir() {
        return (File) conv(destinationDir, "destinationDir");
    }

    /**
     * <p>Sets the directory to generate the documentation into.</p>
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
     *
     * @return The classpath.
     */
    public List<File> getClasspath() {
        return getDependencyManager().resolveTask(getName());
    }

    public String getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }

    /**
     * <p>Returns the title for the generated documentation.</p>
     *
     * @return The title, possibly null.
     */
    public String getTitle() {
        return (String) conv(title, "title");
    }

    /**
     * <p>Sets the title for the generated documentation.</p>
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * <p>Adds include patterns to use to select the java source files to be documented.</p>
     *
     * @param includes A set of ant patterns.
     * @return This task.
     */
    public Javadoc include(String... includes) {
        GUtil.flatten(includes, this.includes);
        return this;
    }

    /**
     * <p>Adds exclude patterns to use to select the java source files to be documented.</p>
     *
     * @param excludes A set of ant patterns.
     * @return This task.
     */
    public Javadoc exclude(String... excludes) {
        GUtil.flatten(excludes, this.excludes);
        return this;
    }

    /**
     * Returns the include patterns to use to select the java source files to be documented.</p>
     *
     * @return The include patterns. Never returns null.
     */
    public List<String> getIncludes() {
        return (List<String>) conv(includes, "includes");
    }

    /**
     * Returns the exclude patterns to use to select the java source files to be documented.</p>
     *
     * @return The exclude patterns. Never returns null.
     */
    public List<String> getExcludes() {
        return (List<String>) conv(excludes, "excludes");
    }

    /**
     * <p>Returns the {@link DependencyManager} which this task uses to resolve the classpath to use when generating the
     * documentation.</p>
     *
     * @return The dependency manager.
     */
    public DependencyManager getDependencyManager() {
        return (DependencyManager) conv(dependencyManager, "dependencyManager");
    }

    /**
     * <p>Sets the {@link DependencyManager} which this task uses to resolve the classpath to use when generating the
     * documentation.</p>
     */
    public void setDependencyManager(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    AntJavadoc getAntJavadoc() {
        return antJavadoc;
    }

    void setAntJavadoc(AntJavadoc antJavadoc) {
        this.antJavadoc = antJavadoc;
    }

    ExistingDirsFilter getExistentDirsFilter() {
        return existentDirsFilter;
    }

    void setExistentDirsFilter(ExistingDirsFilter existentDirsFilter) {
        this.existentDirsFilter = existentDirsFilter;
    }
}
