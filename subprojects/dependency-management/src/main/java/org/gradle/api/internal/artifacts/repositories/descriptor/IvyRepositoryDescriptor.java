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
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.M2ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public final class IvyRepositoryDescriptor extends UrlRepositoryDescriptor {

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    private enum Property {
        IVY_PATTERNS,
        ARTIFACT_PATTERNS,
        LAYOUT_TYPE,
        M2_COMPATIBLE
    }

    private final ImmutableList<String> ivyPatterns;
    private final ImmutableList<ResourcePattern> ivyResources;
    private final ImmutableList<String> artifactPatterns;
    private final ImmutableList<ResourcePattern> artifactResources;
    private final String layoutType;
    private final boolean m2Compatible;

    private IvyRepositoryDescriptor(
        String id,
        String name,
        URI url,
        ImmutableList<String> metadataSources,
        boolean authenticated,
        ImmutableList<String> authenticationSchemes,
        ImmutableList<String> ivyPatterns,
        ImmutableList<ResourcePattern> ivyResources,
        ImmutableList<String> artifactPatterns,
        ImmutableList<ResourcePattern> artifactResources,
        String layoutType,
        boolean m2Compatible
    ) {
        super(id, name, url, metadataSources, authenticated, authenticationSchemes);
        this.ivyPatterns = ivyPatterns;
        this.ivyResources = ivyResources;
        this.artifactPatterns = artifactPatterns;
        this.artifactResources = artifactResources;
        this.layoutType = layoutType;
        this.m2Compatible = m2Compatible;
    }

    @Override
    public ImmutableList<ResourcePattern> getMetadataResources() {
        return ivyResources;
    }

    @Override
    public ImmutableList<ResourcePattern> getArtifactResources() {
        return artifactResources;
    }

    @Override
    public Type getType() {
        return Type.IVY;
    }

    public List<String> getArtifactPatterns() {
        return artifactPatterns;
    }

    public boolean isM2Compatible() {
        return m2Compatible;
    }

    @Override
    protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
        super.addProperties(builder);
        builder.put(Property.IVY_PATTERNS.name(), ivyPatterns);
        builder.put(Property.ARTIFACT_PATTERNS.name(), artifactPatterns);
        builder.put(Property.LAYOUT_TYPE.name(), layoutType);
        builder.put(Property.M2_COMPATIBLE.name(), m2Compatible);
    }

    @NonNullApi
    private static class Resource {
        final URI baseUri;
        final String pattern;

        public Resource(URI baseUri, String pattern) {
            this.baseUri = baseUri;
            this.pattern = pattern;
        }
    }

    public static class Builder extends UrlRepositoryDescriptor.Builder<Builder> {
        private final List<String> ivyPatterns = new ArrayList<>();
        private final List<String> artifactPatterns = new ArrayList<>();
        private String layoutType;
        private Boolean m2Compatible;
        // Artifact resources derived from other configuration
        private final List<Resource> ivyResources = new ArrayList<>();
        private final List<Resource> artifactResources = new ArrayList<>();

        public Builder(String name, URI url) {
            super(name, url);
        }

        public void addIvyPattern(String declaredPattern) {
            ivyPatterns.add(declaredPattern);
        }

        public void addIvyResource(@Nullable URI baseUri, String pattern) {
            if (baseUri != null) {
                ivyResources.add(new Resource(baseUri, pattern));
            }
        }

        public void addArtifactPattern(String declaredPattern) {
            artifactPatterns.add(declaredPattern);
        }

        public void addArtifactResource(@Nullable URI rootUri, String pattern) {
            if (rootUri != null) {
                artifactResources.add(new Resource(rootUri, pattern));
            }
        }

        public Builder setLayoutType(String layoutType) {
            this.layoutType = layoutType;
            return this;
        }

        public Builder setM2Compatible(boolean m2Compatible) {
            this.m2Compatible = m2Compatible;
            return this;
        }

        public IvyRepositoryDescriptor create() {
            checkNotNull(m2Compatible);
            checkNotNull(metadataSources);

            ImmutableList.Builder<ResourcePattern> ivyResourcesBuilder = ImmutableList.builderWithExpectedSize(ivyPatterns.size());
            for (Resource resource : ivyResources) {
                ivyResourcesBuilder.add(toResourcePattern(resource.baseUri, resource.pattern));
            }
            ImmutableList.Builder<ResourcePattern> artifactResourcesBuilder = ImmutableList.builderWithExpectedSize(artifactPatterns.size() + artifactResources.size());
            for (Resource resource : artifactResources) {
                artifactResourcesBuilder.add(toResourcePattern(resource.baseUri, resource.pattern));
            }

            ImmutableList<ResourcePattern> effectiveIvyResources = ivyResourcesBuilder.build();
            ImmutableList<ResourcePattern> effectiveArtifactResources = artifactResourcesBuilder.build();

            String id = calculateId(IvyResolver.class, effectiveIvyResources, effectiveArtifactResources, metadataSources, hasher -> hasher.putBoolean(m2Compatible));

            return new IvyRepositoryDescriptor(
                id,
                checkNotNull(name),
                url,
                metadataSources,
                checkNotNull(authenticated),
                checkNotNull(authenticationSchemes),
                ImmutableList.copyOf(ivyPatterns),
                effectiveIvyResources,
                ImmutableList.copyOf(artifactPatterns),
                effectiveArtifactResources,
                checkNotNull(layoutType),
                m2Compatible
            );
        }

        private ResourcePattern toResourcePattern(URI baseUri, String pattern) {
            return m2Compatible ? new M2ResourcePattern(baseUri, pattern) : new IvyResourcePattern(baseUri, pattern);
        }
    }
}
