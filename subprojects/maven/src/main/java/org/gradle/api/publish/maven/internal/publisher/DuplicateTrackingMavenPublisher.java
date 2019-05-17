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

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import javax.annotation.Nullable;

public class DuplicateTrackingMavenPublisher implements MavenPublisher {
    private final MavenPublisher delegate;
    private final DuplicatePublicationTracker duplicatePublicationTracker;

    public DuplicateTrackingMavenPublisher(MavenPublisher delegate, DuplicatePublicationTracker duplicatePublicationTracker) {
        this.delegate = delegate;
        this.duplicatePublicationTracker = duplicatePublicationTracker;
    }

    @Override
    public void publish(MavenNormalizedPublication publication, @Nullable MavenArtifactRepository artifactRepository) {
        duplicatePublicationTracker.checkCanPublish(publication.getProjectIdentity(), artifactRepository.getUrl(), artifactRepository.getName());
        delegate.publish(publication, artifactRepository);
    }
}
