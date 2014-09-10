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
package org.gradle.internal.resolve;

import org.gradle.internal.component.model.ComponentArtifactIdentifier;

import java.util.List;

public class ArtifactNotFoundException extends ArtifactResolveException {
    public ArtifactNotFoundException(ComponentArtifactIdentifier artifact, List<String> attemptedLocations) {
        super(format(artifact, attemptedLocations));
    }

    private static String format(ComponentArtifactIdentifier artifact, List<String> locations) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Artifact '%s' not found.", artifact.getDisplayName()));
        if (!locations.isEmpty()) {
            builder.append(String.format("%nSearched in the following locations:"));
            for (String location : locations) {
                builder.append(String.format("%n    %s", location.replace("%", "%%")));
            }
        }
        return builder.toString();
    }
}
