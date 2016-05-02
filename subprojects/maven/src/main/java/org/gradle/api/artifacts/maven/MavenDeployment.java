/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.artifacts.maven;

import org.gradle.api.artifacts.PublishArtifact;

import java.util.Set;

/**
 * Represents the artifacts which will be deployed to a Maven repository. You can use this interface to modify the set
 * of artifacts.
 */
public interface MavenDeployment {
    /**
     * Returns the POM for this deployment.
     *
     * @return The POM. Never null.
     */
    PublishArtifact getPomArtifact();

    /**
     * Returns the main artifact for this deployment.
     *
     * @return The main artifact. May be null.
     */
    PublishArtifact getMainArtifact();

    /**
     * Returns the artifacts which will be deployed. Includes the POM artifact.
     *
     * @return The artifacts. Never null.
     */
    Set<PublishArtifact> getArtifacts();

    /**
     * Adds an additional artifact to this deployment.
     *
     * @param artifact The artifact to add.
     */
    void addArtifact(PublishArtifact artifact);

    /**
     * Returns the additional artifacts for this deployment.
     *
     * @return the additional artifacts for this deployment. Never null.
     */
    public Set<PublishArtifact> getAttachedArtifacts();
}
