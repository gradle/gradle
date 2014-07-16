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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.logging.StyledTextOutput;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.reporting.ReportRenderer;
import org.gradle.runtime.base.ProjectBinary;
import org.gradle.runtime.base.ProjectComponent;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class ComponentRenderer extends ReportRenderer<ProjectComponent, TextReportBuilder> {
    private final FileResolver fileResolver;

    public ComponentRenderer(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void render(ProjectComponent component, TextReportBuilder builder) {
        builder.subheading(StringUtils.capitalize(component.getDisplayName()));
        builder.getOutput().println();
        builder.collection("Source sets", component.getSource(), new SourceSetRenderer(), "source sets");
        builder.getOutput().println();
        builder.collection("Binaries", component.getBinaries(), new BinaryRenderer(), "binaries");
    }

    private static class BinaryRenderer extends ReportRenderer<ProjectBinary, TextReportBuilder> {
        @Override
        public void render(ProjectBinary binary, TextReportBuilder builder) throws IOException {
            StyledTextOutput textOutput = builder.getOutput();
            textOutput.println(StringUtils.capitalize(binary.getDisplayName()));
            if (binary instanceof NativeBinary) {
                NativeBinary nativeBinary = (NativeBinary) binary;
                textOutput.formatln("    platform: %s", nativeBinary.getTargetPlatform().getName());
                textOutput.formatln("    build type: %s", nativeBinary.getBuildType().getName());
                textOutput.formatln("    flavor: %s", nativeBinary.getFlavor().getName());
            }
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
