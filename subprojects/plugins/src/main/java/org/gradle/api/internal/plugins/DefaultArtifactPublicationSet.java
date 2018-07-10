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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.DefaultChangingValueHandler;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
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
            artifactStore.addAllLater(defaultArtifactProvider);
        }

        defaultArtifactProvider.addCandidate(artifact);
    }

    public DefaultArtifactProvider getDefaultArtifactProvider() {
        return defaultArtifactProvider;
    }

    private class DefaultArtifactProvider implements CollectionProviderInternal<PublishArtifact, Set<PublishArtifact>>, ChangingValue<Set<PublishArtifact>> {
        private final DefaultChangingValueHandler<Set<PublishArtifact>> valueChangeHandler = new DefaultChangingValueHandler<Set<PublishArtifact>>();
        private final Set<PublishArtifact> artifacts;
        private Set<PublishArtifact> currentArtifactSet;

        DefaultArtifactProvider(PublishArtifact... candidates) {
            artifacts = Sets.newLinkedHashSet(Arrays.asList(candidates));
        }

        @Override
        public void onValueChange(Action<Provider<Set<PublishArtifact>>> action) {
            valueChangeHandler.onValueChange(action);
        }

        @Nullable
        @Override
        public Class<? extends PublishArtifact> getElementType() {
            return PublishArtifact.class;
        }

        @Override
        public int size() {
            return getArtifactSet().size();
        }

        @Override
        public <S> ProviderInternal<S> map(Transformer<? extends S, ? super Set<PublishArtifact>> transformer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<PublishArtifact> get() {
            return getArtifactSet();
        }

        @Override
        public Set<PublishArtifact> getOrElse(Set<PublishArtifact> defaultValue) {
            return getArtifactSet();
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        void addCandidate(PublishArtifact artifact) {
            if (artifact != null) {
                // invalidate the current cached result any time a new artifact is added
                if (artifacts.add(artifact)) {
                    currentArtifactSet = null;
                    valueChangeHandler.valueChanged(this);
                }
            }
        }

        @Nullable
        @Override
        public Class<Set<PublishArtifact>> getType() {
            return null;
        }

        @Nullable
        @Override
        public Set<PublishArtifact> getOrNull() {
            return getArtifactSet();
        }

        private Set<PublishArtifact> getArtifactSet() {
            if (currentArtifactSet == null) {
                List<PublishArtifact> artifactList = Lists.newArrayList();
                String defaultType = null;
                for (PublishArtifact artifact : artifacts) {
                    String newType = artifact.getType();
                    if (artifactList.isEmpty()) {
                        artifactList.add(artifact);
                        defaultType = newType;
                        continue;
                    }
                    if (newType.equals("ear")) {
                        replaceCurrentDefault(artifact, artifactList);
                    } else if (newType.equals("war")) {
                        if (defaultType.equals("jar")) {
                            replaceCurrentDefault(artifact, artifactList);
                        }
                    } else if (!newType.equals("jar")) {
                        artifactList.add(artifact);
                    }
                }
                currentArtifactSet = Sets.newLinkedHashSet(artifactList);
            }

            return currentArtifactSet;
        }

        private void replaceCurrentDefault(PublishArtifact artifact, List<PublishArtifact> artifactList) {
            if (!artifactList.isEmpty()) {
                artifactList.remove(0);
            }
            artifactList.add(0, artifact);
        }
    }
}
