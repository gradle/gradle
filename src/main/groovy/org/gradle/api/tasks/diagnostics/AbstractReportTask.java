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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;

import java.io.IOException;
import java.io.File;
import java.util.Set;
import java.util.TreeSet;

/**
 * The base class for all project report tasks.
 */
public abstract class AbstractReportTask extends ConventionTask {
    private File outputFile;

    public AbstractReportTask(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
    }

    private void generate() {
        try {
            ProjectReportRenderer renderer = getRenderer();
            File outputFile = getOutputFile();
            if (outputFile != null) {
                outputFile.getParentFile().mkdirs();
                renderer.setOutputFile(outputFile);
            }
            Set<Project> projects = new TreeSet<Project>(getProject().getAllprojects());
            for (Project project : projects) {
                renderer.startProject(project);
                generate(project);
                renderer.completeProject(project);
            }
            renderer.complete();
        } catch (IOException e) {
            throw new GradleException(e);
        }
    }

    protected abstract ProjectReportRenderer getRenderer();

    protected abstract void generate(Project project) throws IOException;

    /**
     * Returns the file which the report will be written to. When set to null, the report is written to stdout.
     *
     * @return The output file. May be null.
     */
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * Sets the file which the report will be written to. Set this to null to write the report to stdout.
     *
     * @param outputFile The output file. May be null.
     */
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }
}
