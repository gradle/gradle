/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.state;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.resolver.ResolutionAccess;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.Factory;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.model.CalculatedModelValue;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The state of a configuration that is only needed when it is resolved.
 */
public final class ResolvableState {
    public final ConfigurationResolver resolver;
    /** Discarded once the resolution strategy is created. */
    public @Nullable Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    public @Nullable ResolutionStrategyInternal resolutionStrategy;
    public final ResolutionAccess resolutionAccess;
    public final CalculatedModelValue<Optional<ResolverResults>> currentResolveState;
    public final UserCodeApplicationContext userCodeApplicationContext;
    public final ResolvableDependencies resolvableDependencies;
    public ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners;

    public ImmutableActionSet<DependencySet> withDependencyActions = ImmutableActionSet.empty();
    public @Nullable FileCollectionInternal intrinsicFiles;
    public @Nullable ConfigurationInternal consistentResolutionSource;
    public @Nullable String consistentResolutionReason;
    public List<String> resolutionAlternatives = ImmutableList.of();

    public ResolvableState(
        ConfigurationResolver resolver,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        ResolutionAccess resolutionAccess,
        CalculatedModelValue<Optional<ResolverResults>> currentResolveState,
        UserCodeApplicationContext userCodeApplicationContext,
        ResolvableDependencies resolvableDependencies,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners
    ) {
        this.resolver = resolver;
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.resolutionAccess = resolutionAccess;
        this.currentResolveState = currentResolveState;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.resolvableDependencies = resolvableDependencies;
        this.dependencyResolutionListeners = dependencyResolutionListeners;
    }
}
