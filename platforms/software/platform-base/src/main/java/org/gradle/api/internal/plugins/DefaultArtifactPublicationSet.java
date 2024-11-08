/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.plugins;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.inject.Inject;

/**
 * The policy for which artifacts should be published by default when none are explicitly declared.
 */
public abstract class DefaultArtifactPublicationSet {
    private final PublishArtifactSet artifactContainer;

    @Inject
    public DefaultArtifactPublicationSet(PublishArtifactSet artifactContainer) {
        this.artifactContainer = artifactContainer;
    }

    public void addCandidate(PublishArtifact artifact) {

        DeprecationLogger.deprecateMethod(DefaultArtifactPublicationSet.class, "addCandidate(PublishArtifact)")
            .withContext("DefaultArtifactPublicationSet is deprecated and will be removed in Gradle 9.0.")
            .withAdvice("To ensure the 'assemble' task builds the artifact, use tasks.assemble.dependsOn(artifact).")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_automatically_assembled_artifacts")
            .nagUser();

        // Adding artifacts to the archives configuration also produces a deprecation warning.
        // Avoid the duplicate deprecation warnings.
        DeprecationLogger.whileDisabled(() -> {
            artifactContainer.add(artifact);
        });

    }
}
