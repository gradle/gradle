/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.maven.MavenArtifact;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A MavenPublisher that filters all the invalid artifacts whose ignoreIfAbsent is true.
 */
public class FilteringMavenPublisher implements MavenPublisher {
    private final MavenPublisher delegate;

    public FilteringMavenPublisher(MavenPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(MavenNormalizedPublication publication, @Nullable MavenArtifactRepository artifactRepository) {
        delegate.publish(new FilteringMavenNormalizedPublication(publication), artifactRepository);
    }

    private static class FilteringMavenNormalizedPublication implements MavenNormalizedPublication {
        private final MavenNormalizedPublication delegate;

        private FilteringMavenNormalizedPublication(MavenNormalizedPublication delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public ModuleComponentIdentifier getProjectIdentity() {
            return delegate.getProjectIdentity();
        }

        @Override
        public String getGroupId() {
            return delegate.getGroupId();
        }

        @Override
        public String getArtifactId() {
            return delegate.getArtifactId();
        }

        @Override
        public String getVersion() {
            return delegate.getVersion();
        }

        @Override
        public String getPackaging() {
            return delegate.getPackaging();
        }

        @Override
        @Deprecated
        public File getPomFile() {
            return delegate.getPomFile();
        }

        @Override
        public MavenArtifact getPomArtifact() {
            final MavenArtifact pomArtifact = delegate.getPomArtifact();
            if (pomArtifact.getIgnoreIfAbsent() && !pomArtifact.getFile().exists()) {
                throw new IllegalArgumentException("pom artifact cannot be set as ignoreIfAbsent");
            }

            return pomArtifact;
        }

        @Override
        public MavenArtifact getMainArtifact() {
            final MavenArtifact mainArtifact = delegate.getMainArtifact();
            if (mainArtifact != null && mainArtifact.getIgnoreIfAbsent() && !mainArtifact.getFile().exists()) {
                return null;
            }

            return mainArtifact;
        }

        @Override
        public Set<MavenArtifact> getAdditionalArtifacts() {
            return delegate.getAdditionalArtifacts().stream().filter(mavenArtifact -> {
                return !mavenArtifact.getIgnoreIfAbsent() || mavenArtifact.getFile().exists();
            }).collect(Collectors.toSet());
        }

        @Override
        public Set<MavenArtifact> getAllArtifacts() {
            return delegate.getAllArtifacts().stream().filter(artifact -> {
                return !artifact.getIgnoreIfAbsent() || artifact.getFile().exists();
            }).collect(Collectors.toSet());
        }
    }
}
