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

package org.gradle.api.tasks.javadoc

import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.Project
import org.gradle.execution.Dag

/**
 * @author Hans Dockter
 */
class Javadoc extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Javadoc)

    List srcDirs

    File destinationDir

    String maxMemory

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    AntJavadoc antJavadoc = new AntJavadoc()

    Javadoc(Project project, String name, Dag tasksGraph) {
        super(project, name, tasksGraph);
        doFirst(this.&generate)
    }

    private void generate(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(getDestinationDir(), getSrcDirs())
        antJavadoc.execute(existingSourceDirs, getDestinationDir(), getMaxMemory(), project.ant)
    }

    public List getSrcDirs() {
        return conv(srcDirs, "srcDirs");
    }

    public void setSrcDirs(List srcDirs) {
        this.srcDirs = srcDirs;
    }

    public File getDestinationDir() {
        return conv(destinationDir, "destinationDir");
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
