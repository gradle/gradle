/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.LinkedHashSet;
import java.util.Set;

class BuildableIvyArtifact {

    private final IvyArtifactName ivyArtifactName;
    private final Set<String> configurations = new LinkedHashSet<>();

    public BuildableIvyArtifact(String name, String type, String ext, String classifier) {
        this.ivyArtifactName = new DefaultIvyArtifactName(name, type, ext, classifier);
    }

    public BuildableIvyArtifact addConfiguration(String confName) {
        configurations.add(confName);
        return this;
    }

    public IvyArtifactName getArtifact() {
        return ivyArtifactName;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }
}
