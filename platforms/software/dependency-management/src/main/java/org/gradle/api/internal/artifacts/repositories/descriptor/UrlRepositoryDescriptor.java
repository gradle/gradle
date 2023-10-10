/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.descriptor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public abstract class UrlRepositoryDescriptor extends RepositoryDescriptor {

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    public enum Property {
        URL,
        METADATA_SOURCES,
        AUTHENTICATED,
        AUTHENTICATION_SCHEMES,
    }

    public final URI url;
    public final ImmutableList<String> metadataSources;
    public final boolean authenticated;
    public final ImmutableList<String> authenticationSchemes;

    protected UrlRepositoryDescriptor(
        String id,
        String name,
        URI url,
        ImmutableList<String> metadataSources,
        boolean authenticated,
        ImmutableList<String> authenticationSchemes
    ) {
        super(id, name);
        this.url = url;
        this.metadataSources = metadataSources;
        this.authenticated = authenticated;
        this.authenticationSchemes = authenticationSchemes;
    }

    public abstract ImmutableList<ResourcePattern> getMetadataResources();

    public abstract ImmutableList<ResourcePattern> getArtifactResources();

    @Override
    protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
        if (url != null) {
            builder.put(Property.URL.name(), url);
        }
        builder.put(Property.METADATA_SOURCES.name(), metadataSources);
        builder.put(Property.AUTHENTICATED.name(), authenticated);
        builder.put(Property.AUTHENTICATION_SCHEMES.name(), authenticationSchemes);
    }

    static abstract class Builder<T extends Builder<T>> {
        private static final StringInterner REPOSITORY_ID_INTERNER = new StringInterner();

        final String name;
        final URI url;

        ImmutableList<String> metadataSources;
        Boolean authenticated;
        ImmutableList<String> authenticationSchemes;

        Builder(String name, URI url) {
            this.name = name;
            this.url = url;
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T setMetadataSources(List<String> metadataSources) {
            this.metadataSources = ImmutableList.copyOf(metadataSources);
            return self();
        }

        public T setAuthenticated(boolean authenticated) {
            this.authenticated = authenticated;
            return self();
        }

        public T setAuthenticationSchemes(List<String> authenticationSchemes) {
            this.authenticationSchemes = ImmutableList.copyOf(authenticationSchemes);
            return self();
        }

        protected String calculateId(
            Class<? extends ExternalResourceResolver<?>> implementation,
            List<ResourcePattern> metadataResources,
            List<ResourcePattern> artifactResources,
            List<String> metadataSources,
            Consumer<Hasher> additionalInputs
        ) {
            Hasher cacheHasher = Hashing.newHasher();
            cacheHasher.putString(implementation.getName());
            cacheHasher.putInt(metadataResources.size());
            for (ResourcePattern ivyPattern : metadataResources) {
                cacheHasher.putString(ivyPattern.getPattern());
            }
            cacheHasher.putInt(artifactResources.size());
            for (ResourcePattern artifactPattern : artifactResources) {
                cacheHasher.putString(artifactPattern.getPattern());
            }
            cacheHasher.putInt(metadataResources.size());
            for (String source : metadataSources) {
                cacheHasher.putString(source);
            }
            additionalInputs.accept(cacheHasher);
            return REPOSITORY_ID_INTERNER.intern(cacheHasher.hash().toString());
        }
    }
}
