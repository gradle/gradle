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

import org.gradle.api.publish.maven.MavenArtifact;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenNormalizedPublication {

    private final String name;
    private final MavenProjectIdentity projectIdentity;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String packaging;
    private final MavenArtifact pomArtifact;
    private final MavenArtifact mainArtifact;
    private final Set<MavenArtifact> allArtifacts;

    public MavenNormalizedPublication(String name, MavenProjectIdentity projectIdentity, String packaging, MavenArtifact pomArtifact, MavenArtifact mainArtifact, Set<MavenArtifact> allArtifacts) {
        this.name = name;
        this.projectIdentity = projectIdentity;
        this.groupId = projectIdentity.getGroupId().get();
        this.artifactId = projectIdentity.getArtifactId().get();
        this.version = projectIdentity.getVersion().get();
        this.packaging = packaging;
        this.pomArtifact = pomArtifact;
        this.mainArtifact = mainArtifact;
        this.allArtifacts = allArtifacts;
    }

    public String getName() {
        return name;
    }

    public MavenProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getPackaging() {
        return packaging;
    }

    /**
     * @deprecated Kept to not break third-party plugins
     */
    @Deprecated
    public File getPomFile() {
        return pomArtifact.getFile();
    }

    public MavenArtifact getPomArtifact() {
        return pomArtifact;
    }

    public MavenArtifact getMainArtifact() {
        return mainArtifact;
    }

    public Set<MavenArtifact> getAdditionalArtifacts() {
        return allArtifacts.stream()
                .filter(artifact -> artifact != pomArtifact && artifact != mainArtifact)
                .collect(Collectors.toSet());
    }

    public Set<MavenArtifact> getAllArtifacts() {
        return allArtifacts;
    }
}
