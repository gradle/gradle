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

package org.gradle.api.tasks.compile;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* @author Hans Dockter
*/
public class Compile extends ConventionTask {

    /**
     * The directories with the sources to compile
     */
    private List srcDirs;

    /**
     * The directory where to put the compiled classes (.class files)
     */
    private File destinationDir;

    /**
     * The sourceCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    private String sourceCompatibility;

    /**
     * The targetCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    private String targetCompatibility;

    private FileCollection configuration;

    /**
     * This property is used internally by Gradle. It is usually not used by build scripts.
     * A list of files added to the compile classpath. The files should point to jars or directories containing
     * class files. The files added here are not shared in a multi-project build and are not mentioned in
     * a dependency descriptor if you upload your library to a repository.
     */
    private List unmanagedClasspath;

    /**
     * Options for the compiler. The compile is delegated to the ant javac task. This property contains almost
     * all of the properties available for the ant javac task.
     */
    private CompileOptions options = new CompileOptions();

    /**
     * Include pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package1/')).
     */
    private List includes = new ArrayList();

    /**
     * Exclude pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package2/A*.java').
     */
    private List excludes = new ArrayList();

    protected ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    protected AntJavac antCompile = new AntJavac();

    protected ClasspathConverter classpathConverter = new ClasspathConverter();

    public Compile(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                compile(task);
            }
        });
    }

    protected void compile(Task task) {
        if (antCompile == null) {
            throw new InvalidUserDataException("The ant compile command must be set!");
        }
        getDestinationDir().mkdirs();
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getSrcDirs());

        if (!GUtil.isTrue(getSourceCompatibility()) || !GUtil.isTrue(getTargetCompatibility())) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!");
        }

        antCompile.execute(existingSourceDirs, includes, excludes, getDestinationDir(), getClasspath(), getSourceCompatibility(),
                getTargetCompatibility(), options, getProject().getAnt());
    }

    public List getClasspath() {
        List classpath = GUtil.addLists(classpathConverter.createFileClasspath(getProject().getRootDir(), getUnmanagedClasspath()),
                configuration);
        return classpath;
    }

    /**
     * Add the elements to the unmanaged classpath.
     */
    public Compile unmanagedClasspath(Object... elements) {
        if (unmanagedClasspath == null) {
            List conventionPath = getUnmanagedClasspath();
            if (conventionPath != null) {
                unmanagedClasspath = conventionPath;
            } else {
                unmanagedClasspath = new ArrayList();
            }
        }
        GUtil.flatten(Arrays.asList(elements), unmanagedClasspath);
        return this;
    }

    public Compile include(String[] includes) {
        GUtil.flatten(Arrays.asList(includes), this.includes);
        return this;
    }

    public Compile exclude(String[] excludes) {
        GUtil.flatten(Arrays.asList(excludes), this.excludes);
        return this;
    }

    public List getSrcDirs() {
        return (List) conv(srcDirs, "srcDirs");
    }

    public void setSrcDirs(List srcDirs) {
        this.srcDirs = srcDirs;
    }

    public File getDestinationDir() {
        return (File) conv(destinationDir, "destinationDir");
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public String getSourceCompatibility() {
        return (String) conv(sourceCompatibility, "sourceCompatibility");
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public String getTargetCompatibility() {
        return (String) conv(targetCompatibility, "targetCompatibility");
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public List getUnmanagedClasspath() {
        return (List) conv(unmanagedClasspath, "unmanagedClasspath");
    }

    public void setUnmanagedClasspath(List unmanagedClasspath) {
        this.unmanagedClasspath = unmanagedClasspath;
    }

    public CompileOptions getOptions() {
        return options;
    }

    public void setOptions(CompileOptions options) {
        this.options = options;
    }

    public List getIncludes() {
        return includes;
    }

    public void setIncludes(List includes) {
        this.includes = includes;
    }

    public List getExcludes() {
        return excludes;
    }

    public void setExcludes(List excludes) {
        this.excludes = excludes;
    }
    
    public FileCollection getConfiguration() {
        return configuration;
    }

    public void setConfiguration(FileCollection configuration) {
        this.configuration = configuration;
    }
}
