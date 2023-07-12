/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.publish.maven.internal.validation;

import com.google.common.base.Strings;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;

/**
 * Static util class containing publication checks specific to Maven publications.
 */
public abstract class MavenPublicationErrorChecker extends PublicationErrorChecker {
    /**
     * When the artifacts declared in a component are modified for publishing (name/classifier/extension), then the
     * Maven publication no longer represents the underlying java component. Instead of
     * publishing incorrect metadata, we fail any attempt to publish the module metadata.
     * <p>
     * In the long term, we will likely prevent any modification of artifacts added from a component. Instead, we will
     * make it easier to modify the component(s) produced by a project, allowing the
     * published metadata to accurately reflect the local component metadata.
     * <p>
     * Should only be called after publication is populated from the component.
     *
     * @param source original artifact
     * @param mainArtifacts set of published artifacts to verify
     * @throws PublishException if the artifacts are modified
     */
    public static void checkThatArtifactIsPublishedUnmodified(PublishArtifact source, DefaultMavenArtifactSet mainArtifacts) {
        for (MavenArtifact mavenArtifact : mainArtifacts) {
            String prefix = "Cannot publish module metadata where component artifact (" + mavenArtifact + ") are modified. ";
            if (!source.getFile().equals(mavenArtifact.getFile())) {
                throw new PublishException(prefix + " file differs: " + source.getFile() + " not equal to " + mavenArtifact.getFile());
            }
            if (!source.getExtension().equals(mavenArtifact.getExtension())) {
                throw new PublishException(prefix + " extension differs: " + source.getExtension() + " not equal to " + mavenArtifact.getExtension());
            }
            if (!Strings.nullToEmpty(source.getClassifier()).equals(Strings.nullToEmpty(mavenArtifact.getClassifier()))) {
                throw new PublishException(prefix + " classifier differs: " + source.getClassifier() + " not equal to " + mavenArtifact.getClassifier());
            }
        }
    }
}
