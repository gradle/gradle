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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

@ServiceScope(Scope.Build.class)
public interface DependencyVerificationOverride {
    DependencyVerificationOverride NO_VERIFICATION = original -> original;
    String VERIFICATION_METADATA_XML = "verification-metadata.xml";

    static File dependencyVerificationsFile(File gradleDirectory) {
        return new File(gradleDirectory, VERIFICATION_METADATA_XML);
    }

    ModuleComponentRepository<ExternalModuleComponentGraphResolveState> overrideDependencyVerification(ModuleComponentRepository<ExternalModuleComponentGraphResolveState> original);

    default void buildFinished(GradleInternal model) {
    }

    /**
     * This method is called after we know artifacts have been resolved
     * and that something is actually trying to get the files of an artifact set
     * @param displayName the name of what accessed the artifact
     */
    default void artifactsAccessed(String displayName) {

    }

    default ResolvedArtifactResult verifiedArtifact(ResolvedArtifactResult artifact) {
        return artifact;
    }
}
