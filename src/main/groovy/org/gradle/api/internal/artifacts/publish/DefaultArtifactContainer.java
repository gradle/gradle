/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.ArtifactContainer;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactContainer implements ArtifactContainer {
    private Set<PublishArtifact> artifacts = new HashSet<PublishArtifact>();

    public DefaultArtifactContainer() {
    }
    
    public void addArtifacts(PublishArtifact... publishArtifacts) {
        artifacts.addAll(Arrays.asList(publishArtifacts));
    }

    public Set<PublishArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<PublishArtifact> getArtifacts(Spec<PublishArtifact> spec) {
        return new HashSet<PublishArtifact>(Specs.filterIterable(getArtifacts(), spec));
    }
}
