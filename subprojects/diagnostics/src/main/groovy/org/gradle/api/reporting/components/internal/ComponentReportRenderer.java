/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.logging.StyledTextOutput;
import org.gradle.reporting.ReportRenderer;
import org.gradle.runtime.base.Binary;
import org.gradle.runtime.base.ProjectComponent;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static org.gradle.logging.StyledTextOutput.Style.Info;

public class ComponentReportRenderer extends TextReportRenderer {
    private boolean hasComponents;
    private final FileResolver fileResolver;

    public ComponentReportRenderer(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void complete() {
        getTextOutput().println();
        getTextOutput().println("Note: currently not all plugins register their components, so some components may not be visible here.");
        super.complete();
    }

    @Override
    public void startProject(Project project) {
        super.startProject(project);
        hasComponents = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasComponents) {
            getTextOutput().withStyle(Info).println("No components");
        }

        super.completeProject(project);
    }

    public void startComponent(ProjectComponent component) {
        if (hasComponents) {
            getTextOutput().println();
        }
        getBuilder().subheading(StringUtils.capitalize(component.getDisplayName()));
        hasComponents = true;
    }

    public void renderSourceSets(Collection<? extends LanguageSourceSet> sourceSets) {
        getBuilder().getOutput().println();
        getBuilder().collection("Source sets", sourceSets, new SourceSetRenderer(), "source sets");
    }

    public void renderBinaries(Collection<? extends Binary> binaries) {
        getBuilder().getOutput().println();
        getBuilder().collection("Binaries", binaries, new BinaryRenderer(), "binaries");
    }

    private static class BinaryRenderer extends ReportRenderer<Binary, TextReportBuilder> {
        @Override
        public void render(Binary binary, TextReportBuilder builder) throws IOException {
            StyledTextOutput textOutput = builder.getOutput();
            textOutput.println(StringUtils.capitalize(binary.getDisplayName()));
            textOutput.formatln("    build task: %s", binary.getBuildTask().getPath());
        }
    }

    private class SourceSetRenderer extends ReportRenderer<LanguageSourceSet, TextReportBuilder> {
        @Override
        public void render(LanguageSourceSet sourceSet, TextReportBuilder builder) throws IOException {
            StyledTextOutput textOutput = builder.getOutput();
            textOutput.println(StringUtils.capitalize(sourceSet.toString()));
            Set<File> srcDirs = sourceSet.getSource().getSrcDirs();
            if (srcDirs.isEmpty()) {
                textOutput.println("    No source directories");
            } else {
                for (File file : srcDirs) {
                    textOutput.formatln("    %s", fileResolver.resolveAsRelativePath(file));
                }
            }
        }
    }
}
