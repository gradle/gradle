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
package org.gradle.api.publication.maven.internal;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.MavenDeployment;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

import java.util.HashSet;
import java.util.Set;

public class DefaultMavenDeployment implements MavenDeployment {
    private final String packaging;
    private final MavenProjectIdentity projectIdentity;
    private final PublishArtifact pomArtifact;
    private final PublishArtifact mainArtifact;
    private Set<PublishArtifact> attachedArtifacts;

    public DefaultMavenDeployment(String packaging, MavenProjectIdentity projectIdentity, PublishArtifact pomArtifact, PublishArtifact mainArtifact, Iterable<? extends PublishArtifact> attachedArtifacts) {
        this.packaging = packaging;
        this.projectIdentity = projectIdentity;
        this.pomArtifact = pomArtifact;
        this.mainArtifact = mainArtifact;
        this.attachedArtifacts = Sets.newLinkedHashSet(attachedArtifacts);
    }

    @Override
    public String getPackaging() {
        return packaging;
    }

    @Override
    public String getGroupId() {
        return projectIdentity.getGroupId().get();
    }

    @Override
    public String getArtifactId() {
        return projectIdentity.getArtifactId().get();
    }

    @Override
    public String getVersion() {
        return projectIdentity.getVersion().get();
    }

    @Override
    public void addArtifact(PublishArtifact artifact) {
        attachedArtifacts.add(artifact);
    }

    @Override
    public PublishArtifact getPomArtifact() {
        return pomArtifact;
    }

    @Override
    public Set<PublishArtifact> getArtifacts() {
        Set<PublishArtifact> artifacts = new HashSet<PublishArtifact>();
        artifacts.addAll(attachedArtifacts);
        if (mainArtifact != null) {
            artifacts.add(mainArtifact);
        }
        artifacts.add(pomArtifact);
        return artifacts;
    }

    @Override
    public PublishArtifact getMainArtifact() {
        return mainArtifact;
    }

    @Override
    public Set<PublishArtifact> getAttachedArtifacts() {
        return attachedArtifacts;
    }
}
