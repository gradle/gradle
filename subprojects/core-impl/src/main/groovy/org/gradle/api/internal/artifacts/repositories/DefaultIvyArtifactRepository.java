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
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.plugins.resolver.URLResolver;
import org.gradle.api.artifacts.dsl.IvyArtifactRepository;
import org.gradle.api.internal.file.FileResolver;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultIvyArtifactRepository implements IvyArtifactRepository, ArtifactRepositoryInternal {
    private String name;
    private String username;
    private String password;
    private final Set<String> artifactPatterns = new LinkedHashSet<String>();
    private final FileResolver resolver;

    public DefaultIvyArtifactRepository(FileResolver resolver) {
        this.resolver = resolver;
    }

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        for (String artifactPattern : artifactPatterns) {
            // get rid of the ivy [] token, as [ ] are not valid URI characters
            int pos = artifactPattern.indexOf('[');
            String basePath = pos < 0 ? artifactPattern : artifactPattern.substring(0, pos);
            URI baseUri = resolver.resolveUri(basePath);
            String pattern = pos < 0 ? "" : artifactPattern.substring(pos);
            String absolutePattern = baseUri.toString() + pattern;
            RepositoryResolver resolver;
            if ("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme())) {
                resolver = http();
            } else if ("file".equalsIgnoreCase(baseUri.getScheme())) {
                absolutePattern = baseUri.getPath() + '/' + pattern;
                resolver = file();
            } else {
                resolver = url();
            }
            resolver.setName(name);
            resolver.addArtifactPattern(absolutePattern);
            resolver.addIvyPattern(absolutePattern);

            resolvers.add(resolver);
        }
    }

    private RepositoryResolver url() {
        return new URLResolver();
    }

    private RepositoryResolver file() {
        return new FileSystemResolver();
    }

    private RepositoryResolver http() {
        RepositoryResolver resolver = new RepositoryResolver();
        resolver.setRepository(new CommonsHttpClientBackedRepository(username, password));
        return resolver;
    }

    public void artifactPattern(String pattern) {
        artifactPatterns.add(pattern);
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
}
