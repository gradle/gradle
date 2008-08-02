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
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.execution.Dag;
import org.gradle.util.GUtil;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class Javadoc extends ConventionTask {
    List<File> srcDirs;

    File destinationDir;

    String maxMemory;

    List<String> includes = new ArrayList<String>();

    List<String> excludes = new ArrayList<String>();

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    AntJavadoc antJavadoc = new AntJavadoc();

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
        antJavadoc.execute(existingSourceDirs, getDestinationDir(), getMaxMemory(), getIncludes(), getExcludes(), getProject().getAnt());
    }

    public List<File> getSrcDirs() {
        return (List<File>) conv(srcDirs, "srcDirs");
    }

    public void setSrcDirs(List<File> srcDirs) {
        this.srcDirs = srcDirs;
    }

    public File getDestinationDir() {
        return (File) conv(destinationDir, "destinationDir");
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public String getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }

    public Javadoc include(String... includes) {
        GUtil.flatten(includes, this.includes);
        return this;
    }

    public Javadoc exclude(String... excludes) {
        GUtil.flatten(excludes, this.excludes);
        return this;
    }

    public List<String> getIncludes() {
        return (List<String>) conv(includes, "includes");
    }

    public List<String> getExcludes() {
        return (List<String>) conv(excludes, "excludes");
    }
    
    public AntJavadoc getAntJavadoc() {
        return antJavadoc;
    }

    public void setAntJavadoc(AntJavadoc antJavadoc) {
        this.antJavadoc = antJavadoc;
    }

    public ExistingDirsFilter getExistentDirsFilter() {
        return existentDirsFilter;
    }

    public void setExistentDirsFilter(ExistingDirsFilter existentDirsFilter) {
        this.existentDirsFilter = existentDirsFilter;
    }
}
