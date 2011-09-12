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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.jfrog.wharf.ivy.resolver.UrlWharfResolver;

import java.net.URI;
import java.util.*;

public class DefaultIvyArtifactRepository implements IvyArtifactRepository, ArtifactRepositoryInternal {
    private String name;
    private String username;
    private String password;
    private Object baseUrl;
    private final Set<String> artifactPatterns = new LinkedHashSet<String>();
    private final Set<String> ivyPatterns = new LinkedHashSet<String>();
    private final FileResolver fileResolver;

    public DefaultIvyArtifactRepository(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        List<ResolvedPattern> resolvedArtifactPatterns = resolvePatterns(artifactPatterns, DEFAULT_ARTIFACT_PATTERN);
        List<ResolvedPattern> resolvedIvyPatterns = GUtil.elvis(resolvePatterns(ivyPatterns, DEFAULT_IVY_PATTERN), resolvedArtifactPatterns);
        if (resolvedArtifactPatterns.isEmpty()) {
            throw new InvalidUserDataException("You must specify a base url or at least one artifact pattern for an Ivy repository.");
        }

        Set<String> schemes = getUniqueSchemes(resolvedArtifactPatterns, resolvedIvyPatterns);

        RepositoryResolver resolver = createResolver(schemes);
        resolver.setName(name);

        for (ResolvedPattern resolvedPattern : resolvedArtifactPatterns) {
            resolver.addArtifactPattern(resolvedPattern.absolutePattern);
        }
        for (ResolvedPattern resolvedIvyPattern : resolvedIvyPatterns) {
            resolver.addIvyPattern(resolvedIvyPattern.absolutePattern);
        }
        resolvers.add(resolver);
    }

    private List<ResolvedPattern> resolvePatterns(Set<String> rawPatterns, String defaultPattern) {
        List<ResolvedPattern> resolvedPatterns = new ArrayList<ResolvedPattern>();
        if (baseUrl != null) {
            resolvedPatterns.add(new ResolvedPattern(getUrl(), defaultPattern));
        }
        for (String artifactPattern : rawPatterns) {
            ResolvedPattern pattern = new ResolvedPattern(artifactPattern, fileResolver);
            resolvedPatterns.add(pattern);
        }
        return resolvedPatterns;
    }

    private RepositoryResolver createResolver(Set<String> schemes) {
        if (WrapUtil.toSet("http", "https").containsAll(schemes)) {
            return http();
        }
        if (WrapUtil.toSet("file").containsAll(schemes)) {
            return file();
        }
        return url();
    }

    private Set<String> getUniqueSchemes(List<ResolvedPattern> patterns, List<ResolvedPattern> ivyPatterns) {
        Set<String> schemes = new LinkedHashSet<String>();
        for (ResolvedPattern pattern : patterns) {
            schemes.add(pattern.scheme);
        }
        for (ResolvedPattern pattern : ivyPatterns) {
            schemes.add(pattern.scheme);
        }
        return schemes;
    }

    private RepositoryResolver url() {
        return new UrlWharfResolver();
    }

    private RepositoryResolver file() {
        return new LocalFileSystemResolver(name);
    }

    private RepositoryResolver http() {
        return new CommonsHttpClientResolver(username, password);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserName() {
        return username;
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public URI getUrl() {
        return fileResolver.resolveUri(baseUrl);
    }

    public void setUrl(Object url) {
        baseUrl = url;
    }

    public void artifactPattern(String pattern) {
        artifactPatterns.add(pattern);
    }

    public void ivyPattern(String pattern) {
        ivyPatterns.add(pattern);
    }

    private static class ResolvedPattern {
        public final String scheme;
        public final String absolutePattern;

        public ResolvedPattern(String rawPattern, FileResolver fileResolver) {
            // get rid of the ivy [] token, as [ ] are not valid URI characters
            int pos = rawPattern.indexOf('[');
            String basePath = pos < 0 ? rawPattern : rawPattern.substring(0, pos);
            URI baseUri = fileResolver.resolveUri(basePath);
            String pattern = pos < 0 ? "" : rawPattern.substring(pos);
            scheme = baseUri.getScheme().toLowerCase();
            absolutePattern = constructAbsolutePattern(baseUri, pattern);
        }

        public ResolvedPattern(URI baseUri, String pattern) {
            scheme = baseUri.getScheme().toLowerCase();
            absolutePattern = constructAbsolutePattern(baseUri, pattern);
        }

        private String constructAbsolutePattern(URI baseUri, String patternPart) {
            String uriPart = baseUri.toString();
            String join = uriPart.endsWith("/") || patternPart.length() == 0 ? "" : "/";
            return uriPart + join + patternPart;
        }
    }
}
