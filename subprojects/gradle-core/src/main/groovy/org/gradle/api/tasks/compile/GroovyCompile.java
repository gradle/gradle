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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class GroovyCompile extends Compile {
    private AntGroovyc antGroovyCompile = new AntGroovyc();

    private FileCollection groovyClasspath;

    private GroovyCompileOptions groovyOptions = new GroovyCompileOptions();

    protected void compile() {
        if (getAntGroovyCompile() == null) {
            throw new InvalidUserDataException("The ant groovy compile command must be set!");
        }
        if (getSourceCompatibility() == null || getTargetCompatibility() == null) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!");
        }

        List<File> classpath = GUtil.addLists(getClasspath());
        // todo We need to understand why it is not good enough to put groovy and ant in the task classpath but also Junit. As we don't understand we put the whole testCompile in it right now. It doesn't hurt, but understanding is better :)
        List<File> taskClasspath = new ArrayList<File>(getGroovyClasspath().getFiles());
        throwExceptionIfTaskClasspathIsEmpty(taskClasspath);
        ProjectInternal project = (ProjectInternal) getProject();
        antGroovyCompile.execute(project.getGradle().getIsolatedAntBuilder(), getSource(), getDestinationDir(),
                classpath, getSourceCompatibility(), getTargetCompatibility(), getGroovyOptions(), getOptions(),
                taskClasspath);
        setDidWork(antGroovyCompile.getNumFilesCompiled() > 0);
    }

    private void throwExceptionIfTaskClasspathIsEmpty(Collection<File> taskClasspath) {
        if (taskClasspath.size() == 0) {
            throw new InvalidUserDataException("You must assign a Groovy library to the groovy configuration!");
        }
    }

    /**
     * Gets the options for the groovyc compilation. To set specific options for the nested javac compilation,
     * use {@link #getOptions()}.
     */
    public GroovyCompileOptions getGroovyOptions() {
        return groovyOptions;
    }

    /**
     * Sets the options for the groovyc compilation. To set specific options for the nested javac compilation,
     * use {@link #getOptions()}. Usually you don't set the options, but you modify the existing instance
     * provided by {@link #getGroovyOptions()}  
     * 
     * @param groovyOptions
     */
    public void setGroovyOptions(GroovyCompileOptions groovyOptions) {
        this.groovyOptions = groovyOptions;
    }

    @InputFiles
    public FileCollection getGroovyClasspath() {
        return groovyClasspath;
    }

    public void setGroovyClasspath(FileCollection groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    public AntGroovyc getAntGroovyCompile() {
        return antGroovyCompile;
    }

    public void setAntGroovyCompile(AntGroovyc antGroovyCompile) {
        this.antGroovyCompile = antGroovyCompile;
    }
}
