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
import org.gradle.util.BootstrapUtil;
import org.gradle.util.GUtil;
import org.gradle.api.logging.LogLevel;

import java.util.List;
import java.io.File;


/**
 * This task generates html api doc for Groovy classes. It uses Groovy's Groovydoc tool for this. Please note that
 * the Groovydoc tool has some severe limitations at the moment (for example no doc for properties comments). The
 * version of the Groovydoc that is used, is the one from the Groovy defined in the build script. Please note also,
 * that the Groovydoc tool prints to System.out for many of its statements and does circumvents our logging currently.
 *  
 * @author Hans Dockter
 */
public class Groovydoc extends ConventionTask {
    private List srcDirs;

    private List groovyClasspath;

    private File destinationDir;

    private ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    private AntGroovydoc antGroovydoc = new AntGroovydoc();

    public Groovydoc(Project project, String name) {
        super(project, name);
        captureStandardOutput(LogLevel.INFO);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate(task);
            }
        });
    }

    private void generate(Task task) {
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getSrcDirs());
        List taskClasspath = GUtil.addLists(BootstrapUtil.getAntJarFiles(), getGroovyClasspath());
        antGroovydoc.execute(existingSourceDirs, getDestinationDir(), getProject().getAnt(), taskClasspath);
    }

    /**
     * <p>Returns the source directories containing the groovy source files to generate documentation for.</p>
     *
     * @return The source directories. Never returns null.
     */
    public List getSrcDirs() {
        return (List) conv(srcDirs, "srcDirs");
    }

    /**
     * <p>Sets the source directories containing the groovy source files to generate documentation for.</p>
     */
    public void setSrcDirs(List srcDirs) {
        this.srcDirs = srcDirs;
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
    public List getGroovyClasspath() {
        return (List) conv(groovyClasspath, "groovyClasspath");
    }

    /**
     * <p>Sets the classpath to use to locate classes referenced by the documented source.</p>
     */
    public void setGroovyClasspath(List groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    ExistingDirsFilter getExistentDirsFilter() {
        return existentDirsFilter;
    }

    void setExistentDirsFilter(ExistingDirsFilter existentDirsFilter) {
        this.existentDirsFilter = existentDirsFilter;
    }

    public AntGroovydoc getAntGroovydoc() {
        return antGroovydoc;
    }

    public void setAntGroovydoc(AntGroovydoc antGroovydoc) {
        this.antGroovydoc = antGroovydoc;
    }
}
