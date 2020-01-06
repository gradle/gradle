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

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.invocation.Gradle;

import java.io.File;

public interface DependencyVerificationOverride {
    DependencyVerificationOverride NO_VERIFICATION = new DependencyVerificationOverride() {
        @Override
        public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
            return original;
        }
    };
    String VERIFICATION_METADATA_XML = "verification-metadata.xml";
    String VERIFICATION_KEYRING_GPG = "verification-keyring.gpg";
    String VERIFICATION_KEYRING_DRYRUN_GPG = "verification-keyring-dryrun.gpg";

    static File dependencyVerificationsFile(File buildDirectory) {
        File gradleDir = ensureGradleDirExists(buildDirectory);
        return new File(gradleDir, VERIFICATION_METADATA_XML);
    }

    static File keyringsFile(File buildDirectory) {
        File gradleDir = ensureGradleDirExists(buildDirectory);
        return new File(gradleDir, VERIFICATION_KEYRING_GPG);
    }

    static File ensureGradleDirExists(File buildDirectory) {
        File gradleDir = new File(buildDirectory, "gradle");
        if (!gradleDir.exists()) {
            gradleDir.mkdirs();
        }
        return gradleDir;
    }

    ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original);

    default void buildFinished(Gradle gradle) {

    }

    /**
     * This method is called after we know artifacts have been resolved
     * and that something is actually trying to get the files of an artifact set
     * @param displayName
     */
    default void artifactsAccessed(String displayName) {

    }

    default ResolvedArtifactResult verifiedArtifact(ResolvedArtifactResult artifact) {
        return artifact;
    }
}
