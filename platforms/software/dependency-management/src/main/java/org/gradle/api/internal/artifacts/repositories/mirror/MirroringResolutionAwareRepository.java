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
package org.gradle.api.internal.artifacts.repositories.mirror;

import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.authentication.Authentication;
import org.gradle.internal.Actions;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Wraps a {@link ResolutionAwareRepository} so that {@link #createResolver()} produces a
 * resolver pointed at a mirror URL with mirror credentials. The wrapped repository
 * instance is never mutated; the user's declared model is invisible to mirroring.
 *
 * <p>Content filters are preserved by exposing the same filter from the wrapped
 * repository when it implements {@link ContentFilteringRepository}.
 */
public class MirroringResolutionAwareRepository implements ResolutionAwareRepository, ContentFilteringRepository {

    private final ResolutionAwareRepository delegate;
    private final MirrorDefinition mirror;
    private final Collection<Authentication> mirrorAuthentication;

    public MirroringResolutionAwareRepository(ResolutionAwareRepository delegate, MirrorDefinition mirror, Collection<Authentication> mirrorAuthentication) {
        this.delegate = delegate;
        this.mirror = mirror;
        this.mirrorAuthentication = mirrorAuthentication;
    }

    public MirrorDefinition getMirror() {
        return mirror;
    }

    public ResolutionAwareRepository getDelegate() {
        return delegate;
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        if (delegate instanceof DefaultMavenArtifactRepository) {
            return ((DefaultMavenArtifactRepository) delegate).createMirroredResolver(mirror.getMirrorUrl(), mirrorAuthentication);
        }
        if (delegate instanceof DefaultIvyArtifactRepository) {
            return ((DefaultIvyArtifactRepository) delegate).createMirroredResolver(mirror.getMirrorUrl(), mirrorAuthentication);
        }
        throw new IllegalStateException("Repository mirroring is only supported for Maven and Ivy repositories, got: " + delegate.getClass().getName());
    }

    @Override
    public RepositoryDescriptor getDescriptor() {
        return delegate.getDescriptor();
    }

    @Override
    public Action<? super ArtifactResolutionDetails> getContentFilter() {
        if (delegate instanceof ContentFilteringRepository) {
            return ((ContentFilteringRepository) delegate).getContentFilter();
        }
        return Actions.doNothing();
    }

    @Nullable
    @Override
    public Set<String> getIncludedConfigurations() {
        if (delegate instanceof ContentFilteringRepository) {
            return ((ContentFilteringRepository) delegate).getIncludedConfigurations();
        }
        return null;
    }

    @Nullable
    @Override
    public Set<String> getExcludedConfigurations() {
        if (delegate instanceof ContentFilteringRepository) {
            return ((ContentFilteringRepository) delegate).getExcludedConfigurations();
        }
        return null;
    }

    @Nullable
    @Override
    public Map<Attribute<Object>, Set<Object>> getRequiredAttributes() {
        if (delegate instanceof ContentFilteringRepository) {
            return ((ContentFilteringRepository) delegate).getRequiredAttributes();
        }
        return null;
    }
}
