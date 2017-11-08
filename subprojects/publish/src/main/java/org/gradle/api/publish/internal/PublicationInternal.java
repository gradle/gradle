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
package org.gradle.api.publish.internal;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.publish.Publication;

import javax.annotation.Nullable;

public interface PublicationInternal extends Publication {
    @Nullable
    SoftwareComponentInternal getComponent();

    ModuleVersionIdentifier getCoordinates();

    /**
     * Specifies that this publication is just an alias for another one and should not
     * be considered when converting project dependencies to published metadata.
     */
    boolean isAlias();

    void setAlias(boolean alias);

    /**
     * Provide the file coordinates for the published artifact, if any.
     *
     * @param source The original PublishArtifact
     * @return The name and URI of the published file, or `null` if the source artifact is not published.
     */
    PublishedFile getPublishedFile(PublishArtifact source);

    interface PublishedFile {
        String getName();

        String getUri();
    }
}
