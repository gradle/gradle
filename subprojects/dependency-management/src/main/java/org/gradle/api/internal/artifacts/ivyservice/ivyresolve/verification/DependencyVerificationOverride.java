/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.invocation.Gradle;

import java.io.File;

public interface DependencyVerificationOverride {
    DependencyVerificationOverride NO_VERIFICATION = new DependencyVerificationOverride() {
        @Override
        public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
            return original;
        }

        public void buildFinished(Gradle gradle) {

        }
    };

    static File dependencyVerificationsFile(File buildDirectory) {
        File gradleDir = ensureGradleDirExists(buildDirectory);
        return new File(gradleDir, "verification-metadata.xml");
    }

    static File ensureGradleDirExists(File buildDirectory) {
        File gradleDir = new File(buildDirectory, "gradle");
        if (!gradleDir.exists()) {
            gradleDir.mkdirs();
        }
        return gradleDir;
    }

    ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original);

    void buildFinished(Gradle gradle);
}
