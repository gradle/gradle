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
import org.gradle.api.Action;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.ChangingValueHandler;
import org.gradle.api.internal.provider.CollectionProviderInternal;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Set;

/**
 * The policy for which artifacts should be published by default when none are explicitly declared.
 */
public abstract class DefaultArtifactPublicationSet {
    private final PublishArtifactSet artifactContainer;
    private DefaultArtifactProvider defaultArtifactProvider;

    @Inject
    public DefaultArtifactPublicationSet(PublishArtifactSet artifactContainer) {
        this.artifactContainer = artifactContainer;
    }

    public void addCandidate(PublishArtifact artifact) {
        if (defaultArtifactProvider == null) {
            defaultArtifactProvider = new DefaultArtifactProvider();
            artifactContainer.addAllLater(defaultArtifactProvider);
        }
        defaultArtifactProvider.addArtifact(artifact);
    }

    DefaultArtifactProvider getDefaultArtifactProvider() {
        return defaultArtifactProvider;
    }

    private static class DefaultArtifactProvider extends AbstractMinimalProvider<Set<PublishArtifact>> implements CollectionProviderInternal<PublishArtifact, Set<PublishArtifact>>, ChangingValue<Set<PublishArtifact>> {
        private Set<PublishArtifact> defaultArtifacts;
        private Set<PublishArtifact> artifacts;
        private PublishArtifact currentDefault;
        private final ChangingValueHandler<Set<PublishArtifact>> changingValue = new ChangingValueHandler<Set<PublishArtifact>>();

        void addArtifact(PublishArtifact artifact) {
            if (artifacts == null) {
                artifacts = Sets.newLinkedHashSet();
            }

            if (artifacts.add(artifact) && defaultArtifacts != null) {
                Set<PublishArtifact> previousArtifacts = Sets.newLinkedHashSet(defaultArtifacts);
                defaultArtifacts = null;
                changingValue.handle(previousArtifacts);
            }
        }

        @Override
        public Class<? extends PublishArtifact> getElementType() {
            return PublishArtifact.class;
        }

        @Override
        public int size() {
            return artifacts.size();
        }

        @Nullable
        @Override
        public Class<Set<PublishArtifact>> getType() {
            return null;
        }

        @Override
        protected Value<Set<PublishArtifact>> calculateOwnValue(ValueConsumer consumer) {
            if (defaultArtifacts == null) {
                defaultArtifacts = Sets.newLinkedHashSet();
                currentDefault = null;
                if (artifacts != null) {
                    for (PublishArtifact artifact : artifacts) {
                        String thisType = artifact.getType();

                        if (currentDefault == null) {
                            defaultArtifacts.add(artifact);
                            currentDefault = artifact;
                        } else {
                            String currentType = currentDefault.getType();
                            if (thisType.equals("ear")) {
                                replaceCurrent(artifact);
                            } else if (thisType.equals("war")) {
                                if (currentType.equals("jar")) {
                                    replaceCurrent(artifact);
                                }
                            } else if (!thisType.equals("jar")) {
                                defaultArtifacts.add(artifact);
                            }
                        }
                    }
                }
            }
            return Value.of(defaultArtifacts);
        }

        void replaceCurrent(PublishArtifact artifact) {
            defaultArtifacts.remove(currentDefault);
            defaultArtifacts.add(artifact);
            currentDefault = artifact;
        }

        @Override
        public void onValueChange(Action<Set<PublishArtifact>> action) {
            changingValue.onValueChange(action);
        }
    }
}
