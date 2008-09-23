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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.gradle.execution.Dag
import org.gradle.util.BootstrapUtil


/**
 * @author Hans Dockter
 */
class Groovydoc extends ConventionTask {
    List srcDirs

    List groovyClasspath

    File destinationDir

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    AntGroovydoc antGroovydoc = new AntGroovydoc()

    def self

    Groovydoc(Project project, String name, Dag tasksGraph) {
        super(project, name, tasksGraph);
        doFirst(this.&generate)
        self = this
    }

    private void generate(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getSrcDirs())
        List taskClasspath = BootstrapUtil.antJarFiles + getGroovyClasspath()
        antGroovydoc.execute(existingSourceDirs, getDestinationDir(), project.ant, taskClasspath)
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

    public List getGroovyClasspath() {
        return (List) conv(groovyClasspath, "groovyClasspath");
    }

    public void setGroovyClasspath(List groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    public ExistingDirsFilter getExistentDirsFilter() {
        return existentDirsFilter;
    }

    public void setExistentDirsFilter(ExistingDirsFilter existentDirsFilter) {
        this.existentDirsFilter = existentDirsFilter;
    }

    public AntGroovydoc getAntGroovydoc() {
        return antGroovydoc;
    }

    public void setAntGroovydoc(AntGroovydoc antGroovydoc) {
        this.antGroovydoc = antGroovydoc;
    }
}
