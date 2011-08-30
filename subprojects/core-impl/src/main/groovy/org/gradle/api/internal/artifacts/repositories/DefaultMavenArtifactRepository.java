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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.DualResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.URLResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.ivyservice.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;
import org.jfrog.wharf.ivy.resolver.IBiblioWharfResolver;
import org.jfrog.wharf.ivy.resolver.UrlWharfResolver;

import java.io.File;
import java.net.URI;
import java.util.*;

public class DefaultMavenArtifactRepository implements MavenArtifactRepository, ArtifactRepositoryInternal {
    private final FileResolver fileResolver;
    private String name;
    private Object url;
    private List<Object> additionalUrls = new ArrayList<Object>();

    public DefaultMavenArtifactRepository(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URI getUrl() {
        return url == null ? null : fileResolver.resolveUri(url);
    }

    public void setUrl(Object url) {
        this.url = url;
    }

    public Set<URI> getArtifactUrls() {
        Set<URI> result = new LinkedHashSet<URI>();
        for (Object additionalUrl : additionalUrls) {
            result.add(fileResolver.resolveUri(additionalUrl));
        }
        return result;
    }

    public void artifactUrls(Object... urls) {
        additionalUrls.addAll(Arrays.asList(urls));
    }

    public void setArtifactUrls(Iterable<?> urls) {
        additionalUrls = GUtil.addLists(urls);
    }

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        IBiblioResolver resolver;

        if (rootUri.getScheme().equalsIgnoreCase("file")) {
            resolver = new IBiblioResolver();
            resolver.setRepository(new FileRepository());
            resolver.setRoot(new File(rootUri).getAbsolutePath());
            resolver.setRepositoryCacheManager(new LocalFileRepositoryCacheManager(name));
        } else {
            IBiblioWharfResolver wharfResolver = new IBiblioWharfResolver();
            wharfResolver.setSnapshotTimeout(IBiblioWharfResolver.DAILY);
            resolver = wharfResolver;
            resolver.setRoot(rootUri.toString());
        }

        resolver.setUsepoms(true);
        resolver.setName(name);
        resolver.setPattern(ArtifactRepositoryContainer.MAVEN_REPO_PATTERN);
        resolver.setM2compatible(true);
        resolver.setUseMavenMetadata(true);
        resolver.setChecksums("");

        Collection<URI> artifactUrls = getArtifactUrls();
        if (artifactUrls.isEmpty()) {
            resolver.setDescriptor(IBiblioResolver.DESCRIPTOR_OPTIONAL);
            resolvers.add(resolver);
            return;
        }

        resolver.setName(name + "_poms");

        URLResolver artifactResolver = new UrlWharfResolver();
        artifactResolver.setName(name + "_jars");
        artifactResolver.setM2compatible(true);
        artifactResolver.setChecksums("");
        artifactResolver.addArtifactPattern(rootUri.toString() + '/' + ArtifactRepositoryContainer.MAVEN_REPO_PATTERN);
        for (URI repoUrl : artifactUrls) {
            artifactResolver.addArtifactPattern(repoUrl.toString() + '/' + ArtifactRepositoryContainer.MAVEN_REPO_PATTERN);
        }

        DualResolver dualResolver = new DualResolver();
        dualResolver.setName(name);
        dualResolver.setIvyResolver(resolver);
        dualResolver.setArtifactResolver(artifactResolver);
        dualResolver.setDescriptor(DualResolver.DESCRIPTOR_OPTIONAL);

        resolvers.add(dualResolver);
    }
}
