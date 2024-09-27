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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.GradleException;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.platform.BuildPlatform;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Contextual
public class NoToolchainAvailableException extends GradleException implements ResolutionProvider  {

    public NoToolchainAvailableException(String message) {
        super(message);
    }

    public static NoToolchainAvailableException create(
        JavaToolchainSpec specification,
        BuildPlatform buildPlatform,
        List<JvmToolchainMetadata> candidates
    ) {
        String formattedSpec = String.format(
            "%s for %s on %s",
            specification.getDisplayName(),
            buildPlatform.getOperatingSystem(),
            buildPlatform.getArchitecture().toString().toLowerCase(Locale.ROOT)
        );

        String formattedCandidates = candidates.stream().map(candidate ->
            "  - " + candidate.metadata.getDisplayName() + " - majorVersion: " + candidate.metadata.getJavaMajorVersion() + " vendor: " + candidate.metadata.getVendor().getDisplayName()
        ).collect(Collectors.joining("\n"));

        String message = "Gradle could not find a Java installation on your machine matching the specified requirements: " + formattedSpec + ".\n" +
            "A suitable toolchain could not be downloaded since auto-provisioning is not enabled.\n" +
            "Gradle discovered the following candidate toolchains on your machine, but none matched the requested specification:\n" +
            formattedCandidates;

        return new NoToolchainAvailableException(message);
    }

    @Override
    public List<String> getResolutions() {
        return Collections.singletonList("Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".");
    }
}
