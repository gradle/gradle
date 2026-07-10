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

package org.gradle.jvm.toolchain.internal.install.exceptions;

import org.gradle.api.GradleException;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Arrays;
import java.util.List;

@Contextual
public class ToolchainProvisioningException extends GradleException implements ResolutionProvider {

    public static final String AUTO_DETECTION_RESOLUTION = "Learn more about toolchain auto-detection and auto-provisioning at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".";
    public static final String DOWNLOAD_REPOSITORIES_RESOLUTION = "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + ".";

    private final List<String> resolutions;

    public ToolchainProvisioningException(
        JavaToolchainSpec specification,
        String cause,
        String... resolutions
    ) {
        super(String.format(
            "Cannot find a Java installation on your machine (%s) matching: %s. %s",
            OperatingSystem.current(),
            specification,
            cause));

        this.resolutions = Arrays.asList(resolutions);
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
