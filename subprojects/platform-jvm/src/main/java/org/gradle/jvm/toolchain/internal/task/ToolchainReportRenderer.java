/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.task;

import com.google.common.base.Strings;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe;

import java.util.List;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;

public class ToolchainReportRenderer extends TextReportRenderer {

    public void printToolchain(ReportableToolchain toolchain) {
        StyledTextOutput output = getTextOutput();
        JavaInstallationProbe.ProbeResult probe = toolchain.probe;
        String displayName = probe.getImplementationName() + " " + probe.getImplementationJavaVersion();
        output.withStyle(Identifier).println(" + " + displayName);
        printAttribute("Location", probe.getJavaHome().toString());
        printAttribute("Language Version", probe.getJavaVersion().getMajorVersion());
        printAttribute("Is JDK", String.valueOf(probe.getInstallType() == JavaInstallationProbe.InstallType.IS_JDK));
        printAttribute("Detected by", toolchain.location.getSource());
        output.println();
    }

    private void printAttribute(String key, String value) {
        final String paddedKey = Strings.padEnd(key + ":", 20, ' ');
        getTextOutput().withStyle(Normal).format("     | %s", paddedKey);
        getTextOutput().withStyle(Description).println(value);
    }

    public void printInvalidToolchains(List<ReportableToolchain> invalidToolchains) {
        if(!invalidToolchains.isEmpty()) {
            StyledTextOutput output = getTextOutput();
            output.withStyle(Identifier).println(" + Invalid toolchains");
            for (ReportableToolchain toolchain : invalidToolchains) {
                JavaInstallationProbe.ProbeResult probe = toolchain.probe;
                final String paddedErrorType = Strings.padEnd(probe.getInstallType().name() + ":", 20, ' ');
                getTextOutput().withStyle(Normal).format("     - %s", paddedErrorType);
                getTextOutput().withStyle(Description).println(probe.getError());
            }
            output.println();
        }
    }
}
