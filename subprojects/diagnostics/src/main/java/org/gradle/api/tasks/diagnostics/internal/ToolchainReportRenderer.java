/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.base.Strings;
import org.gradle.internal.jvm.inspection.JavaInstallationCapability;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.List;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;

public class ToolchainReportRenderer extends TextReportRenderer {

    private static final String DETECTED_TOOLCHAIN_INDENT = Strings.repeat(" ", 5);
    private static final String TOOLCHAIN_METADATA_INDENT = Strings.repeat(" ", 2);

    public void printDetectedToolchain(JvmToolchainMetadata toolchain) {
        StyledTextOutput output = getTextOutput();
        JvmInstallationMetadata metadata = toolchain.metadata;
        String displayName = metadata.getDisplayName();
        output.withStyle(Identifier).println(" + " + displayName + " " + metadata.getRuntimeVersion());
        printMetadata(DETECTED_TOOLCHAIN_INDENT, metadata);
        printAttribute(DETECTED_TOOLCHAIN_INDENT, "Detected by", toolchain.location.getSource());
        output.println();
    }

    public void printToolchainMetadata(JvmInstallationMetadata metadata) {
        StyledTextOutput output = getTextOutput();
        String displayName = metadata.getDisplayName();
        output.withStyle(Identifier).println(displayName + " " + metadata.getRuntimeVersion());
        printMetadata(TOOLCHAIN_METADATA_INDENT, metadata);
        output.println();
    }

    private void printMetadata(String indent, JvmInstallationMetadata metadata) {
        printAttribute(indent, "Location", metadata.getJavaHome().toString());
        printAttribute(indent, "Language Version", metadata.getLanguageVersion().getMajorVersion());
        printAttribute(indent, "Vendor", metadata.getVendor().getDisplayName());
        printAttribute(indent, "Architecture", metadata.getArchitecture());
        printAttribute(indent, "Is JDK", String.valueOf(metadata.getCapabilities().containsAll(JavaInstallationCapability.JDK_CAPABILITIES)));
    }

    private void printAttribute(String indent, String key, String value) {
        final String paddedKey = Strings.padEnd(key + ":", 20, ' ');
        getTextOutput().withStyle(Normal).format("%s| %s", indent, paddedKey);
        getTextOutput().withStyle(Description).println(value);
    }

    public void printInvalidToolchains(List<JvmToolchainMetadata> invalidToolchains) {
        if (!invalidToolchains.isEmpty()) {
            StyledTextOutput output = getTextOutput();
            output.withStyle(Identifier).println(" + Invalid toolchains");
            for (JvmToolchainMetadata toolchain : invalidToolchains) {
                JvmInstallationMetadata metadata = toolchain.metadata;
                output.withStyle(Identifier).println("     + " + metadata.getJavaHome());
                printInvalidToolchainErrorLines(toolchain);
            }
            output.println();
        }
    }

    private void printInvalidToolchainErrorLines(JvmToolchainMetadata invalidToolchain) {
        getTextOutput().withStyle(Normal).format("       | %s", Strings.padEnd("Error:", 20, ' '));
        getTextOutput().withStyle(Description).println(invalidToolchain.metadata.getErrorMessage());

        final Throwable errorCause = invalidToolchain.metadata.getErrorCause();
        Throwable cause = errorCause != null ? errorCause.getCause() : null;
        int reportedCauseLines = 0;
        while (cause != null) {
            getTextOutput().withStyle(Normal).format("       | %s", Strings.padEnd("    Caused by:", 20, ' '));
            getTextOutput().withStyle(Description).println(cause.getMessage());
            reportedCauseLines++;

            cause = cause.getCause();

            // Protect against excessively long cause-chains in the outputs.
            if (reportedCauseLines == INVALID_TOOLCHAIN_ERROR_CAUSE_LIMIT && cause != null) {
                // Ellipsize the omitted cause lines:
                getTextOutput().withStyle(Normal).format("       | %s", Strings.padEnd("", 20, ' '));
                getTextOutput().withStyle(Description).println("...");
                break;
            }
        }
    }

    private static final int INVALID_TOOLCHAIN_ERROR_CAUSE_LIMIT = 5;
}
