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
package org.gradle.api.internal.plugins;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;

/**
 * The policy for which artifacts should be published by default when none are explicitly declared.
 */
public class DefaultArtifactPublicationSet {
    private final PublishArtifactSet artifactStore;
    private DefaultArtifactProvider defaultArtifactProvider;

    public DefaultArtifactPublicationSet(PublishArtifactSet artifactStore) {
        this.artifactStore = artifactStore;
    }

    public void addCandidate(PublishArtifact artifact) {
        if (defaultArtifactProvider == null) {
            defaultArtifactProvider = new DefaultArtifactProvider(artifact);
            artifactStore.addLater(defaultArtifactProvider);
        }

        defaultArtifactProvider.addCandidate(artifact);
    }

    public DefaultArtifactProvider getDefaultArtifactProvider() {
        return defaultArtifactProvider;
    }

    private class DefaultArtifactProvider extends AbstractProvider<PublishArtifact> implements Provider<PublishArtifact> {
        private final Set<PublishArtifact> artifacts;
        private PublishArtifact currentDefault;

        DefaultArtifactProvider(PublishArtifact... candidates) {
            artifacts = Sets.newLinkedHashSet(Arrays.asList(candidates));
        }

        void addCandidate(PublishArtifact artifact) {
            if (artifact != null) {
                // invalidate the current cached result any time a new artifact is added
                if (artifacts.add(artifact)) {
                    currentDefault = null;
                }
            }
        }

        @Nullable
        @Override
        public Class<PublishArtifact> getType() {
            return PublishArtifact.class;
        }

        @Nullable
        @Override
        public PublishArtifact getOrNull() {
            return getDefaultArtifact();
        }

        private PublishArtifact getDefaultArtifact() {
            if (currentDefault == null) {
                for (PublishArtifact artifact : artifacts) {
                    if (currentDefault == null) {
                        currentDefault = artifact;
                        continue;
                    }
                    String newType = artifact.getType();
                    String currentType = currentDefault.getType();
                    if (newType.equals("ear")) {
                        replaceCurrent(artifact);
                    } else if (newType.equals("war")) {
                        if (currentType.equals("jar")) {
                            replaceCurrent(artifact);
                        }
                    } else if (!newType.equals("jar")) {
                        artifactStore.add(artifact);
                    }
                }
            }

            return currentDefault;
        }

        private void replaceCurrent(PublishArtifact artifact) {
            artifactStore.remove(currentDefault);
            currentDefault = artifact;
        }
    }
}
