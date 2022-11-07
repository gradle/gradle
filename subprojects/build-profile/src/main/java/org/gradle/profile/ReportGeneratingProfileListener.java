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

import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

@ServiceScope(Scopes.BuildTree.class)
public class ReportGeneratingProfileListener {
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private final StyledTextOutputFactory textOutputFactory;

    public ReportGeneratingProfileListener(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    public void buildFinished(BuildProfile buildProfile) {
        ProfileReportRenderer renderer = new ProfileReportRenderer();
        File file = new File(buildProfile.getBuildDir(), "reports/profile/profile-" + FILE_DATE_FORMAT.format(new Date(buildProfile.getBuildStarted())) + ".html");
        renderer.writeTo(buildProfile, file);
        renderReportUrl(file);
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

