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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

import java.util.Set;
import java.util.stream.Collectors;

public class MavenNormalizedPublication {

    private final String name;
    private final ModuleComponentIdentifier coordinates;
    private final String packaging;
    private final NormalizedMavenArtifact pomArtifact;
    private final NormalizedMavenArtifact mainArtifact;
    private final Set<NormalizedMavenArtifact> allArtifacts;

    public MavenNormalizedPublication(
        String name,
        MavenPublicationCoordinates projectIdentity,
        String packaging,
        NormalizedMavenArtifact pomArtifact,
        NormalizedMavenArtifact mainArtifact,
        Set<NormalizedMavenArtifact> allArtifacts
    ) {
        this.name = name;
        this.coordinates = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(projectIdentity.getGroupId().get(), projectIdentity.getArtifactId().get()), projectIdentity.getVersion().get());
        this.packaging = packaging;
        this.pomArtifact = pomArtifact;
        this.mainArtifact = mainArtifact;
        this.allArtifacts = allArtifacts;
    }

    public String getName() {
        return name;
    }

    public ModuleComponentIdentifier getProjectIdentity() {
        return coordinates;
    }

    public String getGroupId() {
        return coordinates.getGroup();
    }

    public String getArtifactId() {
        return coordinates.getModule();
    }

    public String getVersion() {
        return coordinates.getVersion();
    }

    public String getPackaging() {
        return packaging;
    }

    public NormalizedMavenArtifact getPomArtifact() {
        return pomArtifact;
    }

    public NormalizedMavenArtifact getMainArtifact() {
        if (mainArtifact != null && !mainArtifact.shouldBePublished()) {
            throw new IllegalStateException("Artifact " + mainArtifact.getFile().getName() + " wasn't produced by this build.");
        }
        return mainArtifact;
    }

    public Set<NormalizedMavenArtifact> getAdditionalArtifacts() {
        return allArtifacts.stream()
                .filter(artifact -> artifact != pomArtifact && artifact != mainArtifact)
                .collect(Collectors.toSet());
    }

    public Set<NormalizedMavenArtifact> getAllArtifacts() {
        return allArtifacts;
    }
}
