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

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.layout.*;
import org.gradle.api.internal.artifacts.repositories.transport.DefaultHttpSettings;
import org.gradle.api.internal.artifacts.repositories.transport.HttpSettings;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.WrapUtil;
import org.jfrog.wharf.ivy.resolver.UrlWharfResolver;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultIvyArtifactRepository extends AbstractAuthenticationSupportedRepository implements IvyArtifactRepository, ArtifactRepositoryInternal {
    private String name;
    private Object baseUrl;
    private RepositoryLayout layout;
    private final AdditionalPatternsRepositoryLayout additionalPatternsLayout;
    private final FileResolver fileResolver;

    public DefaultIvyArtifactRepository(FileResolver fileResolver, PasswordCredentials credentials) {
        super(credentials);
        this.fileResolver = fileResolver;
        this.additionalPatternsLayout = new AdditionalPatternsRepositoryLayout(fileResolver);
        this.layout = new GradleRepositoryLayout();
    }

    public String getUserName() {
        nagUser("userName", "username");
        return getCredentials().getUsername();
    }

    public void setUserName(String username) {
        nagUser("userName", "username");
        getCredentials().setUsername(username);
    }

    public String getPassword() {
        nagUser("password", "password");
        return getCredentials().getPassword();
    }

    public void setPassword(String password) {
        nagUser("password", "password");
        getCredentials().setPassword(password);
    }

    private void nagUser(String propertyName, String replacementName) {
        DeprecationLogger.nagUserWith(String.format("The IvyArtifactRepository.%s property has been deprecated. Please credentials { %s = 'value' } instead.", propertyName, replacementName));
    }

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        URI uri = getUrl();

        Set<String> schemes = new LinkedHashSet<String>();
        layout.addSchemes(uri, schemes);
        additionalPatternsLayout.addSchemes(uri, schemes);

        RepositoryResolver resolver = createResolver(schemes);
        resolver.setName(name);
        
        layout.apply(uri, resolver);
        additionalPatternsLayout.apply(uri, resolver);

        if (resolver.getArtifactPatterns().isEmpty()) {
            throw new InvalidUserDataException("You must specify a base url or at least one artifact pattern for an Ivy repository.");
        }
        resolvers.add(resolver);
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

    private RepositoryResolver url() {
        return new UrlWharfResolver();
    }

    private RepositoryResolver file() {
        return new LocalFileSystemResolver(name);
    }

    private RepositoryResolver http() {
        HttpSettings httpSettings = new DefaultHttpSettings(getCredentials());
        return new CommonsHttpClientResolver(httpSettings);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URI getUrl() {
        return baseUrl == null ? null : fileResolver.resolveUri(baseUrl);
    }

    public void setUrl(Object url) {
        baseUrl = url;
    }

    public void artifactPattern(String pattern) {
        additionalPatternsLayout.artifactPatterns.add(pattern);
    }

    public void ivyPattern(String pattern) {
        additionalPatternsLayout.ivyPatterns.add(pattern);
    }

    public void layout(String layoutName) {
        if ("maven".equals(layoutName)) {
            layout = new MavenRepositoryLayout();
        } else if ("pattern".equals(layoutName)) {
            layout = new PatternRepositoryLayout();
        } else {
            layout = new GradleRepositoryLayout();
        }
    }

    public void layout(String layoutName, Closure config) {
        layout(layoutName);
        ConfigureUtil.configure(config, layout);
    }

    /**
     * Layout for applying additional patterns added via {@link #artifactPatterns} and {@link #ivyPatterns}.
     */
    private static class AdditionalPatternsRepositoryLayout extends RepositoryLayout {
        private final FileResolver fileResolver;
        private final Set<String> artifactPatterns = new LinkedHashSet<String>();
        private final Set<String> ivyPatterns = new LinkedHashSet<String>();

        public AdditionalPatternsRepositoryLayout(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        public void apply(URI baseUri, RepositoryResolver resolver) {
            for (String artifactPattern : artifactPatterns) {
                resolver.addArtifactPattern(new ResolvedPattern(artifactPattern, fileResolver).absolutePattern);
            }

            Set<String> usedIvyPatterns = ivyPatterns.isEmpty() ? artifactPatterns : ivyPatterns;
            for (String ivyPattern : usedIvyPatterns) {
                resolver.addIvyPattern(new ResolvedPattern(ivyPattern, fileResolver).absolutePattern);
            }
        }

        @Override
        public void addSchemes(URI baseUri, Set<String> schemes) {
            for (String pattern : artifactPatterns) {
                schemes.add(new ResolvedPattern(pattern, fileResolver).scheme);
            }
            for (String pattern : ivyPatterns) {
                schemes.add(new ResolvedPattern(pattern, fileResolver).scheme);
            }
        }
    }

}
