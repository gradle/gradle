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
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/**
 * The policy for which artifacts should be published by default when none are explicitly declared.
 *
 * @deprecated This class will be removed in Gradle 9.0
 */
@Deprecated
public abstract class DefaultArtifactPublicationSet {
    private final PublishArtifactSet artifactContainer;
    private DefaultArtifactProvider defaultArtifactProvider;
    private final Set<PublishArtifact> internalArtifacts = new HashSet<>();

    @Inject
    public DefaultArtifactPublicationSet(PublishArtifactSet artifactContainer) {
        this.artifactContainer = artifactContainer;
    }

    /**
     * Return true if this artifact was added by user code
     */
    public boolean shouldWarn(PublishArtifact artifact) {
        return !internalArtifacts.contains(artifact);
    }

    public void addCandidateInternal(PublishArtifact artifact, boolean shouldWarn) {
        if (defaultArtifactProvider == null) {
            defaultArtifactProvider = new DefaultArtifactProvider();
            artifactContainer.addAllLater(defaultArtifactProvider);
        }
        defaultArtifactProvider.addArtifact(artifact);
        if (!shouldWarn) {
            internalArtifacts.add(artifact);
        }
    }

    /**
     * @deprecated Call {@code tasks.assemble.dependsOn(Object)} instead.
     */
    // This is called by KMP:
    // https://github.com/JetBrains/kotlin/blob/33ab1871c7a4fad466d77f40f59be16759d091a5/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/configureBinaryFrameworks.kt#L102C1-L103
    // https://github.com/JetBrains/kotlin/blob/33ab1871c7a4fad466d77f40f59be16759d091a5/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/artifacts/KotlinNativeKlibArtifact.kt#L68
    @Deprecated
    public void addCandidate(PublishArtifact artifact) {
        DeprecationLogger.deprecate("DefaultArtifactPublicationSet")
            .withAdvice("Call tasks.assemble.dependsOn(Object) manually to build an artifact when running the 'assemble' task.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_archives_configuration")
            .nagUser();

        addCandidateInternal(artifact, true);
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
            if (artifacts == null) {
                return 0;
            }
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
