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
import org.gradle.internal.scan.UsedByScanPlugin;

import java.net.URI;
import java.util.Collection;
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

    public final ImmutableList<String> ivyPatterns;
    public final ImmutableList<String> artifactPatterns;
    public final String layoutType;
    public final boolean m2Compatible;

    private IvyRepositoryDescriptor(
        String name,
        URI url,
        ImmutableList<String> metadataSources,
        boolean authenticated,
        ImmutableList<String> authenticationSchemes,
        ImmutableList<String> ivyPatterns,
        ImmutableList<String> artifactPatterns,
        String layoutType,
        boolean m2Compatible
    ) {
        super(name, url, metadataSources, authenticated, authenticationSchemes);
        this.ivyPatterns = ivyPatterns;
        this.artifactPatterns = artifactPatterns;
        this.layoutType = layoutType;
        this.m2Compatible = m2Compatible;
    }

    @Override
    public Type getType() {
        return Type.IVY;
    }

    @Override
    protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
        super.addProperties(builder);
        builder.put(Property.IVY_PATTERNS.name(), ivyPatterns);
        builder.put(Property.ARTIFACT_PATTERNS.name(), artifactPatterns);
        builder.put(Property.LAYOUT_TYPE.name(), layoutType);
        builder.put(Property.M2_COMPATIBLE.name(), m2Compatible);
    }

    public List<String> getIvyPatterns() {
        return ivyPatterns;
    }

    public List<String> getArtifactPatterns() {
        return artifactPatterns;
    }

    public static class Builder extends UrlRepositoryDescriptor.Builder<Builder> {

        private ImmutableList<String> ivyPatterns;
        private ImmutableList<String> artifactPatterns;
        private String layoutType;
        private Boolean m2Compatible;

        public Builder(String name, URI url) {
            super(name, url);
        }

        public Builder setIvyPatterns(Collection<String> ivyPatterns) {
            this.ivyPatterns = ImmutableList.copyOf(ivyPatterns);
            return this;
        }

        public Builder setArtifactPatterns(Collection<String> artifactPatterns) {
            this.artifactPatterns = ImmutableList.copyOf(artifactPatterns);
            return this;
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
            return new IvyRepositoryDescriptor(
                checkNotNull(name),
                url,
                checkNotNull(metadataSources),
                checkNotNull(authenticated),
                checkNotNull(authenticationSchemes),
                checkNotNull(ivyPatterns),
                checkNotNull(artifactPatterns),
                checkNotNull(layoutType),
                checkNotNull(m2Compatible)
            );
        }
    }
}
