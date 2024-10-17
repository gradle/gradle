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

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * @param projectDisplayName the {@linkplain org.gradle.api.Project#getDisplayName() project display name}
     * @param buildDir absolute directory that is the base of the build
     * @param componentName name of the component
     * @param source original artifact
     * @param mainArtifacts set of published artifacts to verify
     * @throws PublishException if the artifacts are modified
     */
    public static void checkThatArtifactIsPublishedUnmodified(
        String projectDisplayName,
        Path buildDir,
        String componentName,
        PublishArtifact source,
        DefaultMavenArtifactSet mainArtifacts
    ) {
        // Note: this just verifies that no component artifact has been removed. Additional artifacts are allowed.
        Map<MavenArtifact, Set<ArtifactDifference>> differences = new HashMap<>();
        for (MavenArtifact mavenArtifact : mainArtifacts) {
            EnumSet<ArtifactDifference> differenceSet = EnumSet.noneOf(ArtifactDifference.class);
            if (!source.getFile().equals(mavenArtifact.getFile().get().getAsFile())) {
                differenceSet.add(ArtifactDifference.FILE);
            }
            if (!Objects.equals(source.getClassifier(), mavenArtifact.getClassifier().getOrNull())) {
                differenceSet.add(ArtifactDifference.CLASSIFIER);
            }
            if (!source.getExtension().equals(mavenArtifact.getExtension().get())) {
                differenceSet.add(ArtifactDifference.EXTENSION);
            }
            // If it's all equal, we found a matching artifact that is being published
            if (differenceSet.isEmpty()) {
                return;
            }
            differences.put(mavenArtifact, differenceSet);
        }
        throw new PublishException("Cannot publish module metadata because an artifact from the '" + componentName +
            "' component has been removed. The available artifacts had these problems:\n" + formatDifferences(projectDisplayName, buildDir, source, differences));
    }

    private static final Comparator<Set<ArtifactDifference>> DIFFERENCE_SET_COMPARATOR =
        // Put the artifacts with the least differences first, since they're more likely to be useful
        Comparator.<Set<ArtifactDifference>>comparingInt(Set::size)
            // Prefer FILE differences over CLASSIFIER differences over EXTENSION differences,
            // since different classifiers/extensions are unlikely to be right
            .thenComparing(set -> set.contains(ArtifactDifference.FILE))
            .thenComparing(set -> set.contains(ArtifactDifference.CLASSIFIER))
            .thenComparing(set -> set.contains(ArtifactDifference.EXTENSION));

    private static final Comparator<Map.Entry<MavenArtifact, Set<ArtifactDifference>>> DIFFERENCE_ENTRY_COMPARATOR =
        Map.Entry.<MavenArtifact, Set<ArtifactDifference>>comparingByValue(DIFFERENCE_SET_COMPARATOR)
            // Last ditch effort to make the order deterministic
            .thenComparing(entry -> entry.getKey().getFile().get().getAsFile().toPath());

    private static String formatDifferences(String projectDisplayName, Path buildDir, PublishArtifact source, Map<MavenArtifact, Set<ArtifactDifference>> differencesByArtifact) {
        Stream<String> differencesFormatted = differencesByArtifact.entrySet().stream()
            .sorted(DIFFERENCE_ENTRY_COMPARATOR)
            .limit(3).map(entry -> {
                MavenArtifact artifact = entry.getKey();
                Set<ArtifactDifference> differenceSet = entry.getValue();
                Path artifactPath = buildDir.relativize(artifact.getFile().get().getAsFile().toPath());
                return "- " + artifactPath + ":\n" + formatDifferenceSet(projectDisplayName, buildDir, source, artifact, differenceSet);
            });
        Stream<String> warningForNonPrintedArtifacts = differencesByArtifact.size() > 3
            ? Stream.of("... (" + (differencesByArtifact.size() - 3) + " more artifact(s) not shown)")
            : Stream.empty();
        return Stream.concat(differencesFormatted, warningForNonPrintedArtifacts)
            .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String formatDifferenceSet(String projectDisplayName, Path buildDir, PublishArtifact expected, MavenArtifact actual, Set<ArtifactDifference> differenceSet) {
        return differenceSet.stream().map(diff -> {
            switch (diff) {
                case FILE: {
                    Path expectedFile = buildDir.relativize(expected.getFile().toPath());
                    Path actualFile = buildDir.relativize(actual.getFile().get().getAsFile().toPath());
                    return "\t- file differs (relative to " + projectDisplayName + "): (expected) " + expectedFile + " != (actual) " + actualFile;
                }
                case CLASSIFIER:
                    return "\t- classifier differs: (expected) " + expected.getClassifier() + " != (actual) " + actual.getClassifier().getOrNull();
                case EXTENSION:
                    return "\t- extension differs: (expected) " + expected.getExtension() + " != (actual) " + actual.getExtension().get();
                default:
                    throw new IllegalArgumentException("Unknown difference: " + diff);
            }
        }).collect(Collectors.joining("\n"));
    }

    @NonNullApi
    private enum ArtifactDifference {
        FILE,
        CLASSIFIER,
        EXTENSION,
    }
}
