/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.plugins;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;

/**
 * The policy for which artifacts should be published by default when none are explicitly declared.
 */
public class DefaultArtifactPublicationSet {
    private final PublishArtifactSet artifacts;
    private PublishArtifact defaultArtifact;

    public DefaultArtifactPublicationSet(PublishArtifactSet artifacts) {
        this.artifacts = artifacts;
    }

    public void addCandidate(PublishArtifact artifact) {
        String thisType = artifact.getType();

        if (defaultArtifact == null) {
            artifacts.add(artifact);
            defaultArtifact = artifact;
            return;
        }

        String currentType = defaultArtifact.getType();
        if (thisType.equals("ear")) {
            replaceCurrent(artifact);
        } else if (thisType.equals("war")) {
            if (currentType.equals("jar")) {
                replaceCurrent(artifact);
            }
        } else if (!thisType.equals("jar")) {
            artifacts.add(artifact);
        }
    }

    private void replaceCurrent(PublishArtifact artifact) {
        artifacts.remove(defaultArtifact);
        artifacts.add(artifact);
        defaultArtifact = artifact;
    }
}
