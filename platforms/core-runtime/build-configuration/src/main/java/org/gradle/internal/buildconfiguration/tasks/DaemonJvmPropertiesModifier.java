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

package org.gradle.internal.buildconfiguration.tasks;

import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesGenerator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.platform.BuildPlatform;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.util.Map;

@ServiceScope(Scope.Project.class)
public class DaemonJvmPropertiesModifier {

    public DaemonJvmPropertiesModifier() {
    }

    public void updateJvmCriteria(
        File propertiesFile,
        JavaLanguageVersion toolchainVersion,
        @Nullable String toolchainVendor,
        boolean nativeImageCapable,
        Map<BuildPlatform, URI> downloadUrlsByPlatform
    ) {
        validateToolchainVersion(toolchainVersion);

        DaemonJvmPropertiesGenerator.generate(propertiesFile, toolchainVersion, toolchainVendor, nativeImageCapable, downloadUrlsByPlatform);
    }

    private static void validateToolchainVersion(JavaLanguageVersion version) {
        // TODO: It would be nice to enforce this as part of task configuration instead of at runtime.
        // TODO: Need to consider how to handle future versions of Java that are not yet known. This currently allows any version of Java above the minimum.
        int minimalSupportedVersion = 8;
        if (!version.canCompileOrRun(minimalSupportedVersion)) {
            String exceptionMessage = String.format("Unsupported Java version '%s' provided for the 'jvm-version' option. Gradle can only run with Java %d and above.",
                version, minimalSupportedVersion);
            throw new IllegalArgumentException(exceptionMessage);
        }
    }
}
