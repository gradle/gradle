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
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultMavenNormalizedPublication implements MavenNormalizedPublication {

    private final String name;
    private final ModuleComponentIdentifier coordinates;
    private final String packaging;
    private final MavenArtifact pomArtifact;
    private final MavenArtifact mainArtifact;
    private final Set<MavenArtifact> allArtifacts;

    public DefaultMavenNormalizedPublication(String name, MavenProjectIdentity projectIdentity, String packaging, @Nonnull MavenArtifact pomArtifact, MavenArtifact mainArtifact, Set<MavenArtifact> allArtifacts) {
        this.name = name;
        this.coordinates = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(projectIdentity.getGroupId().get(), projectIdentity.getArtifactId().get()), projectIdentity.getVersion().get());
        this.packaging = packaging;
        this.pomArtifact = pomArtifact;
        this.mainArtifact = mainArtifact;
        this.allArtifacts = allArtifacts;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ModuleComponentIdentifier getProjectIdentity() {
        return coordinates;
    }

    @Override
    public String getGroupId() {
        return coordinates.getGroup();
    }

    @Override
    public String getArtifactId() {
        return coordinates.getModule();
    }

    @Override
    public String getVersion() {
        return coordinates.getVersion();
    }

    @Override
    public String getPackaging() {
        return packaging;
    }

    @Override
    @Deprecated
    public File getPomFile() {
        return pomArtifact.getFile();
    }

    @Override
    public MavenArtifact getPomArtifact() {
        return pomArtifact;
    }

    @Override
    @Nullable
    public MavenArtifact getMainArtifact() {
        return mainArtifact;
    }

    @Override
    @Deprecated
    public Set<MavenArtifact> getAdditionalArtifacts() {
        return allArtifacts.stream()
            .filter(artifact -> artifact != pomArtifact && artifact != mainArtifact)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<MavenArtifact> getAllArtifacts() {
        return allArtifacts;
    }
}
