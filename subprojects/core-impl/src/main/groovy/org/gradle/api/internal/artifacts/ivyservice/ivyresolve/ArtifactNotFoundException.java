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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ArtifactIdentifier;

public class ArtifactNotFoundException extends ArtifactResolveException {
    public ArtifactNotFoundException(ArtifactIdentifier artifact) {
        super(format(artifact));
    }

    private static String format(ArtifactIdentifier artifact) {
        StringBuilder builder = new StringBuilder();
        builder.append("Artifact '");
        formatTo(artifact, builder);
        builder.append("' not found.");
        return builder.toString();
    }
}
