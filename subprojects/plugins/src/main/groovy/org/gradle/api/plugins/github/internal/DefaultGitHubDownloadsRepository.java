/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.github.internal;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.plugins.github.GitHubDownloadsRepository;
import org.gradle.api.publish.internal.NormalizedPublication;
import org.gradle.api.publish.internal.Publisher;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;

import java.net.URI;

public class DefaultGitHubDownloadsRepository implements GitHubDownloadsRepository, ArtifactRepositoryInternal {

    private static final String PATTERN = "[organisation]/[artifact](-[revision])(-[classifier]).[ext]";

    private String name;
    private String user;
    private Object baseUrl = DOWNLOADS_URL_BASE;

    private final PasswordCredentials credentials;
    private final FileResolver fileResolver;
    private final Factory<MavenArtifactRepository> repositoryFactory;

    public DefaultGitHubDownloadsRepository(FileResolver fileResolver, PasswordCredentials credentials, Factory<MavenArtifactRepository> repositoryFactory) {
        this.fileResolver = fileResolver;
        this.credentials = credentials;
        this.repositoryFactory = repositoryFactory;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public URI getBaseUrl() {
        return fileResolver.resolveUri(baseUrl);
    }

    public void setBaseUrl(Object baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() {
        if (name == null) {
            return getDefaultName();
        } else {
            return name;
        }
    }

    private String getDefaultName() {
        String user = getUser();
        return String.format("GitHub Downloads for GitHub user '%s'", user == null ? "" : user);
    }

    public void setName(String name) {
        this.name = name;
    }

    public PasswordCredentials getCredentials() {
        return credentials;
    }

    public void credentials(Closure closure) {
        ConfigureUtil.configure(closure, credentials);
    }

    public DependencyResolver createResolver() {
        MavenArtifactRepository repository = repositoryFactory.create();
        repository.setUrl(getEffectiveRepoUrl());
        repository.setName(getName());
        applyCredentialsTo(repository.getCredentials());

        ArtifactRepositoryInternal repositoryInternal = toArtifactRepositoryInternal(repository);
        MavenResolver resolver = toMavenResolver(repositoryInternal.createResolver());

        resolver.setPattern(PATTERN);
        return resolver;
    }

    private ArtifactRepositoryInternal toArtifactRepositoryInternal(ArtifactRepository artifactRepository) {
        return (ArtifactRepositoryInternal) artifactRepository;
    }

    private MavenResolver toMavenResolver(DependencyResolver dependencyResolver) {
        return (MavenResolver) dependencyResolver;
    }

    private URI getEffectiveRepoUrl() {
        return fileResolver.resolveUri(String.format("%s/%s", getBaseUrl().toString(), getUser()));
    }

    private void applyCredentialsTo(PasswordCredentials other) {
        other.setUsername(credentials.getUsername());
        other.setPassword(credentials.getPassword());
    }

    public <P extends NormalizedPublication> Publisher<P> createPublisher(Class<P> publicationType) {
        return null;
    }

}
