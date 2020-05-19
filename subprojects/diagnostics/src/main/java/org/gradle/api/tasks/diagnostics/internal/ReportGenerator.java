/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Set;


public class ReportGenerator {

    private final ReportRenderer renderer;
    private final BuildClientMetaData buildClientMetaData;
    private final File outputFile;
    private final StyledTextOutputFactory textOutputFactory;
    private final ProjectReportGenerator projectReportGenerator;

    public ReportGenerator(ReportRenderer renderer, BuildClientMetaData buildClientMetaData, @Nullable File outputFile,
                           StyledTextOutputFactory textOutputFactory, ProjectReportGenerator projectReportGenerator) {
        this.renderer = renderer;
        this.buildClientMetaData = buildClientMetaData;
        this.outputFile = outputFile;
        this.textOutputFactory = textOutputFactory;
        this.projectReportGenerator = projectReportGenerator;
    }

    public void generateReport(Set<Project> projects) {
        try {
            ReportRenderer renderer = getRenderer();
            renderer.setClientMetaData(getClientMetaData());
            File outputFile = getOutputFile();
            if (outputFile != null) {
                renderer.setOutputFile(outputFile);
            } else {
                renderer.setOutput(getTextOutputFactory().create(getClass()));
            }
            for (Project project : projects) {
                renderer.startProject(project);
                projectReportGenerator.generateReport(project);
                renderer.completeProject(project);
            }
            renderer.complete();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected ReportRenderer getRenderer() {
        return renderer;
    }

    protected BuildClientMetaData getClientMetaData() {
        return buildClientMetaData;
    }

    /**
     * Returns the file which the report will be written to. When set to {@code null}, the report is written to {@code System.out}.
     *
     * @return The output file. May be null.
     */
    @Nullable
    protected File getOutputFile() {
        return outputFile;
    }

    protected StyledTextOutputFactory getTextOutputFactory() {
        return textOutputFactory;
    }
}
