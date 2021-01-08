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
package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.io.File;
import java.io.IOException;

/**
 * Renders the model of a project report.
 */
public interface ReportRenderer {

    /**
     * Set the build client metadata.
     *
     * @param clientMetaData the build client metadata, never null
     */
    void setClientMetaData(BuildClientMetaData clientMetaData);

    /**
     * Sets the text output for the report. This method must be called before any other methods on this renderer.
     *
     * @param textOutput The text output, never null.
     */
    void setOutput(StyledTextOutput textOutput);

    /**
     * Sets the output file for the report. This method must be called before any other methods on this renderer.
     *
     * @param file The output file, never null.
     */
    void setOutputFile(File file) throws IOException;

    /**
     * Starts visiting a project.
     *
     * @param project The project, never null.
     */
    void startProject(ProjectDetails project);

    /**
     * Completes visiting a project.
     *
     * @param project The project, never null.
     */
    void completeProject(ProjectDetails project);

    /**
     * Completes this report. This method must be called last on this renderer.
     */
    void complete();

}
