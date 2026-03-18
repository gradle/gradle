/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;

/**
 * Report that the JVM used by the worker is not supported by the tool Gradle is trying to run.
 */
@NullMarked
public class UnsupportedWorkerJvmException extends GradleException implements ResolutionProvider {
    public UnsupportedWorkerJvmException(String toolName, VersionNumber toolVersion) {
        super(String.format("%s %s is not compatible with the configured JVM (%s).", toolName, toolVersion, JavaVersion.current()));
    }

    @Override
    public List<String> getResolutions() {
        // TODO: Make this a general purpose exception.
        return Arrays.asList(
            "Find a compatible version of Checkstyle at https://checkstyle.org/releasenotes.html.",
            "Configure the toolchain used by Checkstyle at " + Documentation.userManual("checkstyle_plugin", "sec:checkstyle_configuration").getUrl() + "."
        );
    }
}
