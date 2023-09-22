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
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;

import java.util.EnumSet;
import java.util.Set;

/**
 * Static util class containing publication checks specific to Maven publications.
 */
@NonNullApi
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
        // Do we want to allow _additional_ artifacts to published? If not, we need to completely associate the source and artifacts
        // at a higher level. As-is, this just verifies that none are removed from the components artifacts.
        MavenArtifact bestMatch = null;
        Set<Difference> bestMatchDifferences = null;
        for (MavenArtifact mavenArtifact : mainArtifacts) {
            EnumSet<Difference> difference = EnumSet.noneOf(Difference.class);
            if (!source.getFile().equals(mavenArtifact.getFile())) {
                difference.add(Difference.FILE);
            }
            if (!source.getExtension().equals(mavenArtifact.getExtension())) {
                difference.add(Difference.EXTENSION);
            }
            if (!Strings.nullToEmpty(source.getClassifier()).equals(Strings.nullToEmpty(mavenArtifact.getClassifier()))) {
                difference.add(Difference.CLASSIFIER);
            }
            // If it's all equal, we found a matching artifact that is being published
            if (difference.isEmpty()) {
                return;
            }
            if (bestMatch == null || difference.size() < bestMatchDifferences.size()) {
                bestMatch = mavenArtifact;
                bestMatchDifferences = difference;
            }
        }
        if (bestMatch == null) {
            throw new PublishException("Cannot publish module metadata because it does not contain any artifacts.");
        }
        String differences = getDifferences(source, bestMatch, bestMatchDifferences);
        throw new PublishException("Cannot publish module metadata because an artifact from a component has been removed. The best match had these problems:\n" + differences);
    }

    private static String getDifferences(PublishArtifact source, MavenArtifact bestMatch, Set<Difference> bestMatchDifferences) {
        return bestMatchDifferences.stream().map(diff -> {
            switch (diff) {
                case FILE:
                    return "- file differs from component artifact: (artifact) " + source.getFile() + " != (best match) " + bestMatch.getFile();
                case EXTENSION:
                    return "- extension differs from component artifact: (artifact) " + source.getExtension() + " != (best match) " + bestMatch.getExtension();
                case CLASSIFIER:
                    return "- classifier differs from component artifact: (artifact) " + source.getClassifier() + " != (best match) " + bestMatch.getClassifier();
                default:
                    throw new IllegalArgumentException("Unknown difference: " + diff);
            }
        }).reduce((a, b) -> a + "\n" + b).orElseThrow(() -> new IllegalStateException("Empty differences should not be passed to this method"));
    }

    @NonNullApi
    private enum Difference {
        FILE,
        EXTENSION,
        CLASSIFIER,
    }
}
