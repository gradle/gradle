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

import org.apache.tools.ant.BuildException;
import org.gradle.api.*;
import org.gradle.api.artifacts.ConfigurationResolveInstructionModifier;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.exec.ExecHandleBuilder;
import org.gradle.util.exec.ExecHandle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.FileWriter;
import java.io.BufferedWriter;

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

    private ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    private MinimalJavadocOptions options = new StandardJavadocDocletOptions();

    private ConfigurationResolveInstructionModifier resolveInstructionModifier;

    public Javadoc(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
    }

    private void generate() {
        List<File> existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(getDestinationDir(), getSrcDirs());
        try {
            final File javadocCommandLineFile = new File(getDestinationDir(), "javadoc.options");

            storeJavadocOptions(existingSourceDirs, javadocCommandLineFile);

            final ExecHandleBuilder execHandleBuilder = new ExecHandleBuilder(true)
                    .execDirectory(getProject().getRootDir())
                    .execCommand(System.getProperty("java.home")+"/bin/javadoc")
                    .prependedArguments("-J", options.getJFlags()) // J flags can not be set in the option file
                    .arguments("@"+javadocCommandLineFile.getAbsolutePath());
            final ExecHandle execHandle = execHandleBuilder.getExecHandle();

            switch ( execHandle.startAndWaitForFinish() ) {
                case SUCCEEDED:
                    // Javadoc generation successfull
                    break;
                case ABORTED:
                    // TODO throw stop exception?
                    break;
                case FAILED:
                    throw new GradleException("Javadoc generation failed.", execHandle.getFailureCause());
                default:
                    throw new GradleException("Javadoc generation ended in an unkown end state." + execHandle.getState());
            }

        }
        catch (BuildException e) {
            throw new GradleException("Javadoc generation failed.", e);
        } catch (IOException e) {
            throw new GradleException("Faild to store javadoc options.", e);
        }
    }

    private void storeJavadocOptions(List<File> existingSourceDirs, File javadocOptionsFile) throws IOException {
        options.sourcepath(existingSourceDirs)
                .directory(getDestinationDir())
                .classpath(getClasspath())
                .windowTitle(getTitle());

        if ( maxMemory != null )
            options.jFlags("-Xmx"+maxMemory);

        options.toOptionsFile(new BufferedWriter(new FileWriter(javadocOptionsFile)));
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
        return getDependencyManager().configuration(resolveInstructionModifier.getConfiguration()).resolve(resolveInstructionModifier);
    }

    /**
     * Returns the amount of memory allocated to this task.
     */
    public String getMaxMemory() {
        return maxMemory;
    }

    /**
     * Sets the amount of memory allocated to this task.
     *
     * @param maxMemory The amount of memory
     */
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

    /**
     * Returns whether javadoc generation is accompanied by verbose output.
     *
     * @see #setVerbose(boolean) 
     */
    public boolean isVerbose() {
        return options.isVerbose();
    }

    /**
     * Sets whether javadoc generation is accompanied by verbose output or not. The verbose output is done via println (by the
     * underlying ant task). Thus it is not catched by our logging.
     *
     * @param verbose Whether the output should be verbose.
     */
    public void setVerbose(boolean verbose) {
        if ( verbose )
            options.verbose();
    }

    ExistingDirsFilter getExistentDirsFilter() {
        return existentDirsFilter;
    }

    void setExistentDirsFilter(ExistingDirsFilter existentDirsFilter) {
        this.existentDirsFilter = existentDirsFilter;
    }

    public ConfigurationResolveInstructionModifier getResolveInstruction() {
        return resolveInstructionModifier;
    }

    public void setResolveInstruction(ConfigurationResolveInstructionModifier resolveInstructionModifier) {
        this.resolveInstructionModifier = resolveInstructionModifier;
    }

    public MinimalJavadocOptions getOptions() {
        return options;
    }

    public void setOptions(MinimalJavadocOptions options) {
        this.options = options;
    }
}
