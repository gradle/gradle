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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.ChangingValueHandler;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Controls which artifacts should be automatically added to the {@code archives} configuration, and
 * therefore, when the {@code base} plugin is applied, which artifacts are built by default by the
 * {@code assemble} task.
 * <p>
 * This class acts upon all configurations except a certain aggregate configuration. Most
 * non-{@link Configuration#isVisible() visible} configurations' artifacts, other than those of the
 * aggregate configuration, will be added to the aggregate configuration's artifacts. This class
 * attempts to perform some preferential treatment to some artifact types, like ears and wars,
 * in order to intelligently determine which artifacts should be automatically assembled. This logic
 * has caused more trouble than its worth and should be removed in future Gradle versions.
 * <p>
 * TODO #15639: Deprecate DefaultArtifactPublicationSet and the `archives` configuration.
 *  <a href="https://youtrack.jetbrains.com/issue/KT-74476/KGP-uses-internal-Gradle-API-DefaultArtifactPublicationSet">This internal class is currently in-use by Kotlin</a>
 */
public abstract class DefaultArtifactPublicationSet {

    private final DefaultArtifactProvider defaultArtifactProvider;

    @Inject
    public DefaultArtifactPublicationSet(ConfigurationContainer configurations, String aggregateArtifactsConfName) {
        this.defaultArtifactProvider = new DefaultArtifactProvider(configurations, aggregateArtifactsConfName);
        configurations.getByName(aggregateArtifactsConfName).getArtifacts().addAllLater(defaultArtifactProvider);
    }

    public void addCandidate(PublishArtifact artifact) {
        defaultArtifactProvider.addArtifact(artifact);
    }

    DefaultArtifactProvider getDefaultArtifactProvider() {
        return defaultArtifactProvider;
    }

    /**
     * A special Provider implementation determines which artifacts should be automatically added to the
     * {@code archives} configuration, and therefore which should be built automatically by the {@code assemble} task.
     * <p>
     * By implementing {@link ChangingValue} and {@link CollectionProviderInternal}, this provider is able to notify containers
     * which reference this provider that its value has changed. This contrasts with other providers, whose values are cached
     * and never updated when a parent domain object container's value is read. This causes some complicated interactions and
     * dependencies with eager plugins, like the signing plugin.
     * <p>
     * All of this logic should be removed in future Gradle versions.
     */
    private static class DefaultArtifactProvider extends AbstractMinimalProvider<Set<PublishArtifact>> implements CollectionProviderInternal<PublishArtifact, Set<PublishArtifact>>, ChangingValue<Set<PublishArtifact>> {

        private final ConfigurationContainer configurations;
        private final String aggregateArtifactsConfName;

        private Set<PublishArtifact> defaultArtifacts;
        private Set<PublishArtifact> explicitArtifacts;
        private ImmutableSet<PublishArtifact> aggregateArtifacts;
        private PublishArtifact currentDefault;
        private final ChangingValueHandler<Set<PublishArtifact>> changingValue = new ChangingValueHandler<Set<PublishArtifact>>();

        public DefaultArtifactProvider(ConfigurationContainer configurations, String aggregateArtifactsConfName) {
            this.configurations = configurations;
            this.aggregateArtifactsConfName = aggregateArtifactsConfName;

            // Whenever artifacts are added to any configuration, invalidate our cached value.
            // The new value will be calculated lazily when it is next requested.
            configurations.configureEach(conf -> {
                if (!conf.getName().equals(aggregateArtifactsConfName)) {
                    conf.getArtifacts().configureEach(artifact -> {
                        reset();
                    });
                }
            });
        }

        void addArtifact(PublishArtifact artifact) {
            if (explicitArtifacts == null) {
                explicitArtifacts = new LinkedHashSet<>();
            }

            // A new artifact was added explicitly, invalidate our cached value.
            // The new value will be calculated lazily when it is next requested.
            if (explicitArtifacts.add(artifact)) {
                reset();
            }
        }

        private void reset() {
            if (defaultArtifacts != null) {
                aggregateArtifacts = null;

                Set<PublishArtifact> previousArtifacts = new LinkedHashSet<>(defaultArtifacts);
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
            if (explicitArtifacts == null) {
                return getAggregateConfigurationArtifacts().size();
            }
            return explicitArtifacts.size() + getAggregateConfigurationArtifacts().size();
        }

        @Nullable
        @Override
        public Class<Set<PublishArtifact>> getType() {
            return null;
        }

        @Override
        protected Value<Set<PublishArtifact>> calculateOwnValue(ValueConsumer consumer) {
            if (defaultArtifacts == null) {
                defaultArtifacts = new LinkedHashSet<>();
                currentDefault = null;
                if (explicitArtifacts != null) {
                    for (PublishArtifact artifact : explicitArtifacts) {
                        processArtifact(artifact);
                    }
                }
                for (PublishArtifact artifact : getAggregateConfigurationArtifacts())  {
                    processArtifact(artifact);
                }
            }
            return Value.of(defaultArtifacts);
        }

        // TODO #26418: Deprecate assemble building all artifacts of all visible configurations.
        private ImmutableSet<PublishArtifact> getAggregateConfigurationArtifacts() {
            if (aggregateArtifacts == null) {
                aggregateArtifacts = configurations.stream()
                    .filter(conf -> !conf.getName().equals(aggregateArtifactsConfName) && conf.isVisible())
                    .flatMap(conf -> conf.getArtifacts().stream())
                    .collect(ImmutableSet.toImmutableSet());
            }
            return aggregateArtifacts;
        }

        /**
         * Logic which attempts to determine which artifacts should be included in {@link #defaultArtifacts}.
         * This can cause confusing behavior to users.
         *
         * @see <a href="https://github.com/gradle/gradle/issues/6875">#6875</a>
         * @see <a href="https://github.com/gradle/gradle/issues/26418">#26418</a>
         */
        private void processArtifact(PublishArtifact artifact) {
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
