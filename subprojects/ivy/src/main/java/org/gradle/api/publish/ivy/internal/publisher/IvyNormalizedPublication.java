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

import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyArtifactSet;

import java.io.File;
import java.util.Set;

public class IvyNormalizedPublication {

    private final String name;
    private final IvyPublicationIdentity projectIdentity;
    private final File ivyDescriptorFile;
    private final Set<IvyArtifact> allArtifacts;
    private final IvyArtifactSet mainArtifacts;

    public IvyNormalizedPublication(String name, IvyArtifactSet mainArtifacts, IvyPublicationIdentity projectIdentity, File ivyDescriptorFile, Set<IvyArtifact> allArtifacts) {
        this.name = name;
        this.projectIdentity = projectIdentity;
        this.ivyDescriptorFile = ivyDescriptorFile;
        this.allArtifacts = allArtifacts;
        this.mainArtifacts = mainArtifacts;
    }

    public String getName() {
        return name;
    }

    public IvyPublicationIdentity getProjectIdentity() {
        return projectIdentity;
    }

    public File getIvyDescriptorFile() {
        return ivyDescriptorFile;
    }

    public Set<IvyArtifact> getAllArtifacts() {
        assertMainArtifactsPublishable();
        return allArtifacts;
    }

    private void assertMainArtifactsPublishable() {
        mainArtifacts.all(artifact -> {
            if (artifact.getClassifier() == null) {
                if (!((PublicationArtifactInternal) artifact).shouldBePublished()) {
                    throw new IllegalStateException("Artifact " + artifact.getFile().getName() + " wasn't produced by this build.");
                }
            }
        });
    }
}
