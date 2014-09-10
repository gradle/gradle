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

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentArtifactIdentifier;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.util.GUtil;

@Contextual
public class ArtifactResolveException extends GradleException {
    public ArtifactResolveException(String message) {
        super(message);
    }

    public ArtifactResolveException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArtifactResolveException(ComponentIdentifier component, Throwable cause) {
        super(format(component, ""), cause);
    }

    public ArtifactResolveException(ComponentIdentifier component, String message) {
        super(format(component, message));
    }

    public ArtifactResolveException(ComponentArtifactIdentifier artifact, Throwable cause) {
        super(format(artifact, ""), cause);
    }

    public ArtifactResolveException(ComponentArtifactIdentifier artifact, String message) {
        super(format(artifact, message));
    }

    private static String format(ComponentArtifactIdentifier artifact, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("Could not download artifact '");
        builder.append(artifact.getDisplayName());
        builder.append("'");
        if (GUtil.isTrue(message)) {
            builder.append(": ");
            builder.append(message);
        }
        return builder.toString();
    }

    private static String format(ComponentIdentifier component, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("Could not determine artifacts for component '");
        builder.append(component.getDisplayName());
        builder.append("'");
        if (GUtil.isTrue(message)) {
            builder.append(": ");
            builder.append(message);
        }
        return builder.toString();
    }
}
