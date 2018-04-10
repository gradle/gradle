/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher;

import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.publish.maven.MavenArtifact;

import java.io.File;
import java.util.Set;

public class MavenNormalizedPublication {

    private final String name;
    private final File pomFile;
    private final File metadataFile;
    private final MavenProjectIdentity projectIdentity;
    private final Set<MavenArtifact> mavenArtifacts;
    private final Set<PublicationArtifact> additionalArtifacts;
    private final MavenArtifact mainArtifact;

    public MavenNormalizedPublication(String name, File pomFile, File metadataFile, MavenProjectIdentity projectIdentity, Set<MavenArtifact> mavenArtifacts, MavenArtifact mainArtifact, Set<PublicationArtifact> additionalArtifacts) {
        this.name = name;
        this.pomFile = pomFile;
        this.metadataFile = metadataFile;
        this.projectIdentity = projectIdentity;
        this.mavenArtifacts = mavenArtifacts;
        this.mainArtifact = mainArtifact;
        this.additionalArtifacts = additionalArtifacts;
    }

    public String getName() {
        return name;
    }

    public File getPomFile() {
        return pomFile;
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    public Set<MavenArtifact> getMavenArtifacts() {
        return mavenArtifacts;
    }

    public Set<PublicationArtifact> getAdditionalArtifacts() {
        return additionalArtifacts;
    }

    public MavenProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

    public MavenArtifact getMainArtifact() {
        return mainArtifact;
    }

}
