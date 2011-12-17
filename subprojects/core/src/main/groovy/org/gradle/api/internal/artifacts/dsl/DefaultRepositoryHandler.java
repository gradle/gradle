/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.repositories.FixedResolverArtifactRepository;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandler extends DefaultArtifactRepositoryContainer implements RepositoryHandler, ResolverProvider {
    public DefaultRepositoryHandler(ResolverFactory resolverFactory, Instantiator instantiator) {
        super(resolverFactory, instantiator);
    }

    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return addRepository(getResolverFactory().createFlatDirRepository(), action, "flatDir");
    }

    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return addRepository(getResolverFactory().createFlatDirRepository(), configureClosure, "flatDir");
    }

    public FlatDirectoryArtifactRepository flatDir(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("dirs")) {
            modifiedArgs.put("dirs", toList(modifiedArgs.get("dirs")));
        }
        return addRepository(getResolverFactory().createFlatDirRepository(), modifiedArgs, "flatDir");
    }

    public MavenArtifactRepository mavenCentral() {
        return mavenCentral(Collections.<String, Object>emptyMap());
    }

    public MavenArtifactRepository mavenCentral(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            DeprecationLogger.nagUserWith("The 'urls' property of the RepositoryHandler.mavenCentral() method is deprecated and will be removed in a future version of Gradle. "
                    + "You should use the 'artifactUrls' property to define additional artifact locations.");
            List<Object> urls = toList(modifiedArgs.remove("urls"));
            modifiedArgs.put("artifactUrls", urls);
        }
        return addRepository(getResolverFactory().createMavenCentralRepository(), modifiedArgs, DEFAULT_MAVEN_CENTRAL_REPO_NAME);
    }

    public MavenArtifactRepository mavenLocal() {
        return addRepository(getResolverFactory().createMavenLocalRepository(), DEFAULT_MAVEN_LOCAL_REPO_NAME);
    }

    public DependencyResolver mavenRepo(Map<String, ?> args) {
        return mavenRepo(args, null);
    }

    public DependencyResolver mavenRepo(Map<String, ?> args, Closure configClosure) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            List<Object> urls = toList(modifiedArgs.remove("urls"));
            if (!urls.isEmpty()) {
                DeprecationLogger.nagUserWith("The 'urls' property of the RepositoryHandler.mavenRepo() method is deprecated and will be removed in a future version of Gradle. "
                        + "You should use the 'url' property to define the core maven repository & the 'artifactUrls' property to define any additional artifact locations.");
                modifiedArgs.put("url", urls.get(0));
                List<Object> extraUrls = urls.subList(1, urls.size());
                modifiedArgs.put("artifactUrls", extraUrls);
            }
        }

        MavenArtifactRepository repository = getResolverFactory().createMavenRepository();
        ConfigureUtil.configureByMap(modifiedArgs, repository);
        DependencyResolver resolver = toResolver(DependencyResolver.class, repository);
        ConfigureUtil.configure(configClosure, resolver);
        addRepository(new FixedResolverArtifactRepository(resolver), "maven");
        return resolver;
    }

    private List<Object> toList(Object object) {
        if (object instanceof List) {
            return (List<Object>) object;
        }
        if (object instanceof Iterable) {
            return GUtil.addLists((Iterable) object);
        }
        return Collections.singletonList(object);
    }

    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        return addRepository(getResolverFactory().createMavenRepository(), action, "maven");
    }

    public MavenArtifactRepository maven(Closure closure) {
        return addRepository(getResolverFactory().createMavenRepository(), closure, "maven");
    }

    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return addRepository(getResolverFactory().createIvyRepository(), action, "ivy");
    }

    public IvyArtifactRepository ivy(Closure closure) {
        return addRepository(getResolverFactory().createIvyRepository(), closure, "ivy");
    }

    public File getMavenPomDir() {
        return null;
    }
}
