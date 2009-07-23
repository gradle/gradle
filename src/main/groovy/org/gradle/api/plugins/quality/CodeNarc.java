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
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.ExistingDirsFilter;

import java.io.File;
import java.util.List;

public class CodeNarc extends ConventionTask {
    private AntCodeNarc antCodeNarc = new AntCodeNarc();

    private List<File> srcDirs;
    private File reportFile;
    private File configFile;

    public CodeNarc(Project project, String name) {
        super(project, name);
    }

    @TaskAction
    public void check() {
        List<File> srcDirs = getSrcDirs();
        new ExistingDirsFilter().findExistingDirsAndThrowStopActionIfNone(srcDirs);
        File reportFile = getReportFile();
        reportFile.getParentFile().mkdirs();
        antCodeNarc.execute(getAnt(), srcDirs, getConfigFile(), reportFile);
    }

    public List<File> getSrcDirs() {
        return srcDirs;
    }

    public void setSrcDirs(List<File> srcDirs) {
        this.srcDirs = srcDirs;
    }

    public File getConfigFile() {
        return configFile;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }
}
