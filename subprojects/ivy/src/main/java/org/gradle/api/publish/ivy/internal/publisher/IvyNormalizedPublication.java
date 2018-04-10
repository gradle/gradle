/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.publish.ivy.IvyArtifact;

import java.io.File;
import java.util.Set;

public class IvyNormalizedPublication {

    private final String name;
    private final IvyPublicationIdentity projectIdentity;
    private final PublicationArtifact ivyDescriptorArtifact;
    private final PublicationArtifact gradleModuleDescriptorArtifact;
    private final Set<PublicationArtifact> allArtifacts;

    public IvyNormalizedPublication(String name, IvyPublicationIdentity projectIdentity, PublicationArtifact ivyDescriptorArtifact, PublicationArtifact gradleModuleDescriptorArtifact, Set<PublicationArtifact> allArtifacts) {
        this.name = name;
        this.projectIdentity = projectIdentity;
        this.ivyDescriptorArtifact = ivyDescriptorArtifact;
        this.gradleModuleDescriptorArtifact = gradleModuleDescriptorArtifact;
        this.allArtifacts = allArtifacts;
    }

    public String getName() {
        return name;
    }

    public IvyPublicationIdentity getProjectIdentity() {
        return projectIdentity;
    }

    public PublicationArtifact getIvyDescriptorArtifact() {
        return ivyDescriptorArtifact;
    }

    public PublicationArtifact getGradleModuleDescriptorArtifact() {
        return gradleModuleDescriptorArtifact;
    }

    public Set<PublicationArtifact> getAllArtifacts() {
        return allArtifacts;
    }
}
