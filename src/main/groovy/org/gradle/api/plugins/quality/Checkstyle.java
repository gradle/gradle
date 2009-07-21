/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.quality;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.apache.tools.ant.BuildException;

import java.io.File;
import java.util.List;

public class Checkstyle extends ConventionTask {
    private List<File> srcDirs;

    private File configFile;

    private File resultFile;

    private FileCollection classpath;

    private AntCheckstyle antCheckstyle = new AntCheckstyle();

    public Checkstyle(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
    }

    private void generate() {
        List<File> existingSrcDirs = new ExistingDirsFilter().findExistingDirsAndThrowStopActionIfNone(getSrcDirs());
        File resultFile = getResultFile();
        resultFile.getParentFile().mkdirs();
        antCheckstyle.checkstyle(getAnt(), existingSrcDirs, getConfigFile(), resultFile, getClasspath());
    }

    public File getConfigFile() {
        return configFile;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    public File getResultFile() {
        return resultFile;
    }

    public void setResultFile(File resultFile) {
        this.resultFile = resultFile;
    }

    public List<File> getSrcDirs() {
        return srcDirs;
    }

    public void setSrcDirs(List<File> srcDirs) {
        this.srcDirs = srcDirs;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }
}
