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
import org.gradle.internal.exceptions.Contextual;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.platform.BuildPlatform;

@Contextual
public class NoToolchainAvailableException extends GradleException {

    public NoToolchainAvailableException(
        JavaToolchainSpec specification,
        BuildPlatform buildPlatform,
        ToolchainDownloadFailedException cause
    ) {
        super(
            String.format(
                "Cannot find a Java installation on your machine matching this tasks requirements: %s for %s on %s.",
                specification.getDisplayName(),
                buildPlatform.getOperatingSystem(),
                buildPlatform.getArchitecture().toString().toLowerCase()
            ),
            cause
        );
    }
}
