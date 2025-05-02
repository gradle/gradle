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
package org.gradle.profile;

import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

@ServiceScope(Scope.BuildTree.class)
public class ReportGeneratingProfileListener {
    private final StyledTextOutputFactory textOutputFactory;
    private final BuildStateRegistry buildStateRegistry;

    public ReportGeneratingProfileListener(
        StyledTextOutputFactory textOutputFactory,
        BuildStateRegistry buildStateRegistry
    ) {
        this.textOutputFactory = textOutputFactory;
        this.buildStateRegistry = buildStateRegistry;
    }

    public void buildFinished(BuildProfile buildProfile) {
        ProfileReportRenderer renderer = new ProfileReportRenderer();
        SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        File file = new File(getBuildDir(), "reports/profile/profile-" + fileDateFormat.format(new Date(buildProfile.getBuildStarted())) + ".html");
        renderer.writeTo(buildProfile, file);
        renderReportUrl(file);
    }

    private File getBuildDir() {
        return buildStateRegistry
            .getRootBuild()
            .getProjects()
            .getRootProject()
            .getMutableModel()
            .getServices()
            .get(ProjectLayout.class)
            .getBuildDirectory()
            .getAsFile()
            .get();
    }

    private void renderReportUrl(File reportFile) {
        StyledTextOutput textOutput = textOutputFactory.create(ReportGeneratingProfileListener.class, LogLevel.LIFECYCLE);
        textOutput.println();
        String reportUrl = new ConsoleRenderer().asClickableFileUrl(reportFile);
        textOutput.formatln("See the profiling report at: %s", reportUrl);
        textOutput.text("A fine-grained performance profile is available: use the ");
        textOutput.withStyle(UserInput).format("--%s", StartParameterBuildOptions.BuildScanOption.LONG_OPTION);
        textOutput.text(" option.");
        textOutput.println();
    }
}

