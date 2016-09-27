/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.descriptor;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

public class MutableModuleDescriptorState extends ModuleDescriptorState {

    public MutableModuleDescriptorState(ModuleComponentIdentifier componentIdentifier) {
        super(componentIdentifier, "integration", true);
    }

    public MutableModuleDescriptorState(ModuleComponentIdentifier componentIdentifier, String status, boolean generated) {
        super(componentIdentifier, status, generated);
    }

    public static MutableModuleDescriptorState createModuleDescriptor(ModuleComponentIdentifier componentIdentifier, Set<IvyArtifactName> componentArtifacts) {
        MutableModuleDescriptorState moduleDescriptorState = new MutableModuleDescriptorState(componentIdentifier);

        for (IvyArtifactName artifactName : componentArtifacts) {
            moduleDescriptorState.addArtifact(artifactName, Collections.singleton(org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION));
        }

        if (componentArtifacts.isEmpty()) {
            IvyArtifactName defaultArtifact = new DefaultIvyArtifactName(componentIdentifier.getModule(), "jar", "jar");
            moduleDescriptorState.addArtifact(defaultArtifact, Collections.singleton(org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION));
        }

        return moduleDescriptorState;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public void setPublicationDate(Date publicationDate) {
        this.publicationDate = publicationDate;
    }

    public void addExclude(Exclude exclude) {
        excludes.add(exclude);
    }
}
