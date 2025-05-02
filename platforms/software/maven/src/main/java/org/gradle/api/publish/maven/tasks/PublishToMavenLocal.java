/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.tasks;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.serialization.Cached;
import org.gradle.work.DisableCachingByDefault;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to the Maven Local repository.
 *
 * @since 1.4
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PublishToMavenLocal extends AbstractPublishToMaven {
    private final Cached<MavenNormalizedPublication> normalizedPublication = Cached.of(this::computeNormalizedPublication);

    private MavenNormalizedPublication computeNormalizedPublication() {
        MavenPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        return publicationInternal.asNormalisedPublication();
    }

    @TaskAction
    public void publish() {
        MavenNormalizedPublication normalizedPublication = this.normalizedPublication.get();
        getDuplicatePublicationTracker().checkCanPublishToMavenLocal(normalizedPublication);
        doPublish(normalizedPublication);
    }

    private void doPublish(MavenNormalizedPublication normalizedPublication) {
        new PublishOperation(normalizedPublication.getName(), "mavenLocal") {
            @Override
            protected void publish() {
                MavenPublisher localPublisher = getMavenPublishers().getLocalPublisher(getTemporaryDirFactory());
                MavenPublisher validatingPublisher = new ValidatingMavenPublisher(localPublisher);
                validatingPublisher.publish(normalizedPublication, null);
            }
        }.run();
    }
}
