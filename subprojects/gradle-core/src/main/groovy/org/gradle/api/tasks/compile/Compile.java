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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
* @author Hans Dockter
*/
public class Compile extends ConventionTask implements PatternFilterable {

    /**
     * The directories with the sources to compile
     */
    private List<File> srcDirs;

    /**
     * The directory where to put the compiled classes (.class files)
     */
    private File destinationDir;

    /**
     * The directory to store cached dependency information.  Used only if
     * options.dependOptions.useCache = true.
     */
    private File dependencyCacheDir;

    /**
     * The sourceCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    private String sourceCompatibility;

    /**
     * The targetCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    private String targetCompatibility;

    private FileCollection classpath;

    private PatternFilterable patternSet = new PatternSet();

    /**
     * Options for the compiler. The compile is delegated to the ant javac task. This property contains almost
     * all of the properties available for the ant javac task.
     */
    private CompileOptions options = new CompileOptions();

    protected ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    protected AntJavac antCompile = new AntJavac();

    @TaskAction
    protected void compile() {
        if (antCompile == null) {
            throw new InvalidUserDataException("The ant compile command must be set!");
        }
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getSrcDirs());

        if (!GUtil.isTrue(getSourceCompatibility()) || !GUtil.isTrue(getTargetCompatibility())) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!");
        }

        antCompile.execute(existingSourceDirs, patternSet.getIncludes(), patternSet.getExcludes(), getDestinationDir(),
                getDependencyCacheDir(), getClasspath(), getSourceCompatibility(), getTargetCompatibility(), options, getProject().getAnt());
        setDidWork(antCompile.getNumFilesCompiled() > 0);
    }

    @InputFiles
    public Iterable<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection configuration) {
        this.classpath = configuration;
    }

    public Compile include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    public Compile include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public Compile exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public Compile exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @InputFiles
    public List<File> getSrcDirs() {
        return srcDirs;
    }

    public void setSrcDirs(List<File> srcDirs) {
        this.srcDirs = srcDirs;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @OutputDirectory
    public File getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }

    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public CompileOptions getOptions() {
        return options;
    }

    public void setOptions(CompileOptions options) {
        this.options = options;
    }

    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    public Compile setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    public Compile setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    public void setAntCompile(AntJavac antCompile) {
        this.antCompile = antCompile;
    }
}
