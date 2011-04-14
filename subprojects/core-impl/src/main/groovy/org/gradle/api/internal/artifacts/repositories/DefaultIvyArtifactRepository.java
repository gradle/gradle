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
import org.apache.ivy.plugins.resolver.URLResolver;
import org.gradle.api.artifacts.dsl.IvyArtifactRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class DefaultIvyArtifactRepository implements IvyArtifactRepository, ArtifactRepositoryInternal {
    private String name;
    private String username;
    private String password;
    private final Set<String> artifactPatterns = new LinkedHashSet<String>();

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        List<String> httpPatterns = new ArrayList<String>();
        List<String> otherPatterns = new ArrayList<String>();

        for (String artifactPattern : artifactPatterns) {
            try {
                URI uri = new URI(artifactPattern.replaceAll("\\[.*\\]", "token"));
                if (uri.getScheme() != null && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                    httpPatterns.add(artifactPattern);
                    continue;
                }
            } catch (URISyntaxException e) {
                // Ignore
            }
            otherPatterns.add(artifactPattern);
        }

        if (!otherPatterns.isEmpty()) {
            URLResolver resolver = new URLResolver();
            resolver.setName(name);
            for (String artifactPattern : otherPatterns) {
                resolver.addArtifactPattern(artifactPattern);
                resolver.addIvyPattern(artifactPattern);
            }
            resolvers.add(resolver);
        }

        if (!httpPatterns.isEmpty()) {
            RepositoryResolver resolver = new RepositoryResolver();
            resolver.setRepository(new CommonsHttpClientBackedRepository(username, password));
            resolver.setName(name);
            for (String artifactPattern : httpPatterns) {
                resolver.addArtifactPattern(artifactPattern);
                resolver.addIvyPattern(artifactPattern);
            }
            resolvers.add(resolver);
        }
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
