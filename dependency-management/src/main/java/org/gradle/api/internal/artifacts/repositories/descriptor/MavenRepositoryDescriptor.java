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

import static com.google.common.base.Preconditions.checkNotNull;

public final class MavenRepositoryDescriptor extends UrlRepositoryDescriptor {

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    private enum Property {
        ARTIFACT_URLS,
    }

    public final ImmutableList<URI> artifactUrls;

    private MavenRepositoryDescriptor(
        String name,
        URI url,
        ImmutableList<String> metadataSources,
        boolean authenticated,
        ImmutableList<String> authenticationSchemes,
        ImmutableList<URI> artifactUrls
    ) {
        super(name, url, metadataSources, authenticated, authenticationSchemes);
        this.artifactUrls = artifactUrls;
    }

    @Override
    public Type getType() {
        return Type.MAVEN;
    }

    @Override
    protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
        super.addProperties(builder);
        builder.put(Property.ARTIFACT_URLS.name(), artifactUrls);
    }

    public static class Builder extends UrlRepositoryDescriptor.Builder<Builder> {

        private ImmutableList<URI> artifactUrls;

        public Builder(String name, URI url) {
            super(name, url);
        }

        public Builder setArtifactUrls(Collection<URI> artifactUrls) {
            this.artifactUrls = ImmutableList.copyOf(artifactUrls);
            return this;
        }

        public MavenRepositoryDescriptor create() {
            return new MavenRepositoryDescriptor(
                checkNotNull(name),
                url,
                checkNotNull(metadataSources),
                checkNotNull(authenticated),
                checkNotNull(authenticationSchemes),
                checkNotNull(artifactUrls)
            );
        }
    }
}
