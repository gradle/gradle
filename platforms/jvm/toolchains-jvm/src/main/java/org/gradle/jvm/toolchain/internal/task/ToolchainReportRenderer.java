/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.List;

import static org.gradle.internal.jvm.inspection.JvmInstallationMetadata.JavaInstallationCapability.JAVA_COMPILER;
import static org.gradle.internal.logging.text.StyledTextOutput.Style;

public class ToolchainReportRenderer extends TextReportRenderer {

    public void printToolchain(JvmToolchainMetadata toolchain) {
        StyledTextOutput output = getTextOutput();
        JvmInstallationMetadata metadata = toolchain.metadata;
        String displayName = metadata.getDisplayName();
        output.withStyle(Style.Identifier).println(" + " + displayName + " " + metadata.getRuntimeVersion());
        printAttribute("Location", metadata.getJavaHome().toString());
        printAttribute("Language Version", metadata.getLanguageVersion().getMajorVersion());
        printAttribute("Vendor", metadata.getVendor().getDisplayName());
        printAttribute("Architecture", metadata.getArchitecture());
        printAttribute("Is JDK", String.valueOf(metadata.hasCapability(JAVA_COMPILER)));
        printAttribute("Detected by", toolchain.location.getSource());
        output.println();
    }

    private void printAttribute(String key, String value) {
        final String paddedKey = Strings.padEnd(key + ":", 20, ' ');
        getTextOutput().withStyle(Style.Normal).format("     | %s", paddedKey);
        getTextOutput().withStyle(Style.Description).println(value);
    }

    public void printInvalidToolchains(List<JvmToolchainMetadata> invalidToolchains) {
        if (!invalidToolchains.isEmpty()) {
            StyledTextOutput output = getTextOutput();
            output.withStyle(Style.Identifier).println(" + Invalid toolchains");
            for (JvmToolchainMetadata toolchain : invalidToolchains) {
                JvmInstallationMetadata metadata = toolchain.metadata;
                output.withStyle(Style.Identifier).println("     + " + metadata.getJavaHome());
                printInvalidToolchainErrorLines(toolchain);
            }
            output.println();
        }
    }

    private void printInvalidToolchainErrorLines(JvmToolchainMetadata invalidToolchain) {
        getTextOutput().withStyle(Style.Normal).format("       | %s", Strings.padEnd("Error:", 20, ' '));
        getTextOutput().withStyle(Style.Description).println(invalidToolchain.metadata.getErrorMessage());

        final Throwable errorCause = invalidToolchain.metadata.getErrorCause();
        Throwable cause = errorCause != null ? errorCause.getCause() : null;
        int reportedCauseLines = 0;
        while (cause != null) {
            getTextOutput().withStyle(Style.Normal).format("       | %s", Strings.padEnd("    Caused by:", 20, ' '));
            getTextOutput().withStyle(Style.Description).println(cause.getMessage());
            reportedCauseLines++;

            cause = cause.getCause();

            // Protect against excessively long cause-chains in the outputs.
            if (reportedCauseLines == INVALID_TOOLCHAIN_ERROR_CAUSE_LIMIT && cause != null) {
                // Ellipsize the omitted cause lines:
                getTextOutput().withStyle(Style.Normal).format("       | %s", Strings.padEnd("", 20, ' '));
                getTextOutput().withStyle(Style.Description).println("...");
                break;
            }
        }
    }

    private static final int INVALID_TOOLCHAIN_ERROR_CAUSE_LIMIT = 5;
}
