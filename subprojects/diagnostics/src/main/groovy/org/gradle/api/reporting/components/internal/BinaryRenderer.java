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
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.logging.StyledTextOutput;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.reporting.ReportRenderer;
import org.gradle.runtime.base.ProjectBinary;

class BinaryRenderer extends ReportRenderer<ProjectBinary, TextReportBuilder> {
    @Override
    public void render(ProjectBinary binary, TextReportBuilder builder) {
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
