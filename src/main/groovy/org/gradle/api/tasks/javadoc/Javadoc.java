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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.artifacts.ConfigurationResolveInstructionModifier;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.exec.ExecHandle;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.external.javadoc.JavadocExecHandleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * <p>Generates Javadoc from a number of java source directories.</p>
 *
 * @author Hans Dockter
 */
public class Javadoc extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Javadoc.class);

    private JavadocExecHandleBuilder javadocExecHandleBuilder;

    private List<File> srcDirs;
    private File classesDir;

    private File destinationDir;

    private DependencyManager dependencyManager;

    private boolean failOnError = true;

    private String title;

    private String maxMemory;

    private ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    private String optionsFilename = "javadoc.options";
    private MinimalJavadocOptions options = new StandardJavadocDocletOptions();
    private boolean alwaysAppendDefaultSourcepath = false;
    private boolean alwaysAppendDefaultClasspath = false;

    private ConfigurationResolveInstructionModifier resolveInstructionModifier;

    public Javadoc(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
        javadocExecHandleBuilder = new JavadocExecHandleBuilder();
    }

    private void generate() {
        List<File> existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(getDestinationDir(), getSrcDirs());

        final File destinationDir = getDestinationDir();

        if ( !destinationDir.exists() ) {
            if ( !destinationDir.mkdirs() )
                throw new GradleException("Failed to create destination directory " + destinationDir.getAbsolutePath());
        }

        if ( options.getDestinationDirectory() == null )
            options
                .destinationDirectory(destinationDir);

        if ( options.getSourcepath().isEmpty() || alwaysAppendDefaultSourcepath )
            options
                .sourcepath(existingSourceDirs);

        if ( options.getClasspath().isEmpty() || alwaysAppendDefaultClasspath ) {
            options
                .classpath(getClasspath())
                .classpath(getClassesDir());
        }

        if ( StringUtils.isEmpty(options.getWindowTitle()) )
            options
                .windowTitle("\""+getTitle()+"\"");

        if (    options.getPackageNames().isEmpty() &&
                options.getSourceNames().isEmpty() &&
                options.getSubPackages().isEmpty() ) {
            Set<String> subPackagesToAdd = new HashSet<String>();
            for ( File srcDir : getSrcDirs() ) {
                if ( srcDir.exists() ) {
                    for ( File packageDir : srcDir.listFiles() ) {
                        if ( packageDir.isDirectory() ) {
                            final String packageDirName = packageDir.getName();

                            subPackagesToAdd.add(packageDirName);
                        }
                    }
                }
            }
            for ( String subPackageToAdd : subPackagesToAdd ) {
                options.subPackages(subPackageToAdd);

                if ( logger.isDebugEnabled() ) {
                    logger.debug("Added {} package to subPackages Javadoc option", subPackageToAdd);
                }
            }
        }

        if ( maxMemory != null ) {
            final List<String> jFlags = options.getJFlags();
            final Iterator<String> jFlagsIt = jFlags.iterator();
            boolean containsXmx = false;
            while ( !containsXmx && jFlagsIt.hasNext() ) {
                final String jFlag = jFlagsIt.next();
                if ( jFlag.startsWith("-Xmx") )
                    containsXmx = true;
            }
            if ( !containsXmx )
                options.jFlags("-Xmx"+maxMemory);
        }

        executeExternalJavadoc();
    }

    private void executeExternalJavadoc() {
        javadocExecHandleBuilder
                .execDirectory(getProject().getRootDir())
                .options(options)
                .optionsFilename(optionsFilename)
                .destinationDirectory(getDestinationDir());

        final ExecHandle execHandle = javadocExecHandleBuilder.getExecHandle();

        switch ( execHandle.startAndWaitForFinish() ) {
            case SUCCEEDED:
                break;
            case ABORTED:
                throw new GradleException("Javadoc generation ended in aborted state (should not happen)." + execHandle.getState());
            case FAILED:
                if ( failOnError )
                    throw new GradleException("Javadoc generation failed.", execHandle.getFailureCause());
                else
                    break;
            default:
                throw new GradleException("Javadoc generation ended in an unkown end state." + execHandle.getState());
        }
    }

    void setJavadocExecHandleBuilder(JavadocExecHandleBuilder javadocExecHandleBuilder) {
        if ( javadocExecHandleBuilder == null ) throw new IllegalArgumentException("javadocExecHandleBuilder == null!");
        this.javadocExecHandleBuilder = javadocExecHandleBuilder;
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

    public File getClassesDir() {
        return (File) conv(classesDir, "classesDir");
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

    public List<String> getExclude() {
        return options.getExclude();
    }

    public void exclude(String ... exclude) {
        options.exclude(exclude);
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

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public String getOptionsFilename() {
        return optionsFilename;
    }

    public void setOptionsFilename(String optionsFilename) {
        if ( StringUtils.isEmpty(optionsFilename) ) throw new IllegalArgumentException("optionsFilename can't be empty!");
        this.optionsFilename = optionsFilename;
    }

    public boolean isAlwaysAppendDefaultSourcepath() {
        return alwaysAppendDefaultSourcepath;
    }

    public void setAlwaysAppendDefaultSourcepath(boolean alwaysAppendDefaultSourcepath) {
        this.alwaysAppendDefaultSourcepath = alwaysAppendDefaultSourcepath;
    }

    public Javadoc alwaysAppendDefaultSourcepath() {
        setAlwaysAppendDefaultSourcepath(true);
        return this;
    }

    public boolean isAlwaysAppendDefaultClasspath() {
        return alwaysAppendDefaultClasspath;
    }

    public void setAlwaysAppendDefaultClasspath(boolean alwaysAppendDefaultClasspath) {
        this.alwaysAppendDefaultClasspath = alwaysAppendDefaultClasspath;
    }

    public Javadoc alwaysAppendDefaultClasspath() {
        setAlwaysAppendDefaultClasspath(true);
        return this;
    }
}
