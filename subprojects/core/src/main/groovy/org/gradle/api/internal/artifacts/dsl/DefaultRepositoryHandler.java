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
import org.gradle.api.internal.Actions;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.repositories.FixedResolverArtifactRepository;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.util.CollectionUtils.flattenToList;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandler extends DefaultArtifactRepositoryContainer implements RepositoryHandler, ResolverProvider {

    private final RepositoryFactoryInternal repositoryFactory;

    public DefaultRepositoryHandler(RepositoryFactoryInternal repositoryFactory, Instantiator instantiator) {
        super(repositoryFactory.getBaseRepositoryFactory(), instantiator);
        this.repositoryFactory = repositoryFactory;
    }

    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return addRepository(repositoryFactory.flatDir(action));
    }

    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return flatDir(new ClosureBackedAction<FlatDirectoryArtifactRepository>(configureClosure));
    }

    public FlatDirectoryArtifactRepository flatDir(Map<String, ?> args) {
        return addRepository(repositoryFactory.flatDir(args));
    }

    public MavenArtifactRepository mavenCentral() {
        return addRepository(repositoryFactory.mavenCentral());
    }

    public MavenArtifactRepository mavenCentral(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            DeprecationLogger.nagUserWith("The 'urls' property of the RepositoryHandler.mavenCentral() method is deprecated and will be removed in a future version of Gradle. "
                    + "You should use the 'artifactUrls' property to define additional artifact locations.");
            List<?> urls = flattenToList(modifiedArgs.remove("urls"));
            modifiedArgs.put("artifactUrls", urls);
        }

        MavenArtifactRepository repo = repositoryFactory.mavenCentral();
        ConfigureUtil.configureByMap(modifiedArgs, repo);
        return addRepository(repo);
    }

    public MavenArtifactRepository mavenLocal() {
        return addRepository(repositoryFactory.mavenLocal());
    }

    public DependencyResolver mavenRepo(Map<String, ?> args) {
        return mavenRepo(args, null);
    }

    public DependencyResolver mavenRepo(Map<String, ?> args, Closure configClosure) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            List<?> urls = flattenToList(modifiedArgs.remove("urls"));
            if (!urls.isEmpty()) {
                DeprecationLogger.nagUserWith("The 'urls' property of the RepositoryHandler.mavenRepo() method is deprecated and will be removed in a future version of Gradle. "
                        + "You should use the 'url' property to define the core maven repository & the 'artifactUrls' property to define any additional artifact locations.");
                modifiedArgs.put("url", urls.get(0));
                List<?> extraUrls = urls.subList(1, urls.size());
                modifiedArgs.put("artifactUrls", extraUrls);
            }
        }

        MavenArtifactRepository repository = repositoryFactory.maven(Actions.doNothing());
        ConfigureUtil.configureByMap(modifiedArgs, repository);
        DependencyResolver resolver = repositoryFactory.getBaseRepositoryFactory().toResolver(repository);
        ConfigureUtil.configure(configClosure, resolver);
        addRepository(new FixedResolverArtifactRepository(resolver));
        return resolver;
    }


    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        return addRepository(repositoryFactory.maven(action));
    }

    public MavenArtifactRepository maven(Closure closure) {
        return maven(new ClosureBackedAction<MavenArtifactRepository>(closure));
    }

    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return addRepository(repositoryFactory.ivy(action));
    }

    public IvyArtifactRepository ivy(Closure closure) {
        return ivy(new ClosureBackedAction<IvyArtifactRepository>(closure));
    }

}
