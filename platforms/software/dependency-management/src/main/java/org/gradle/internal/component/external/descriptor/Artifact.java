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

import com.google.common.collect.Sets;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Set;

public class Artifact {
    private final IvyArtifactName artifactName;
    private final Set<String> configurations;

    public Artifact(IvyArtifactName artifactName) {
        this(artifactName, Sets.newLinkedHashSet());
    }

    public Artifact(IvyArtifactName artifactName, Set<String> configurations) {
        this.artifactName = artifactName;
        this.configurations = configurations;
    }

    public IvyArtifactName getArtifactName() {
        return artifactName;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }
}
