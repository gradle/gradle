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
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.DefaultResolverContainer;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandler extends DefaultResolverContainer implements RepositoryHandler {
    private final Set<String> repositoryNames = new HashSet<String>();

    public DefaultRepositoryHandler(ResolverFactory resolverFactory, FileResolver fileResolver, Instantiator instantiator) {
        super(resolverFactory, fileResolver, instantiator);
    }

    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return addRepository(getResolverFactory().createFlatDirRepository(), action, "flatDir");
    }

    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return addRepository(getResolverFactory().createFlatDirRepository(), configureClosure, "flatDir");
    }

    public FileSystemResolver flatDir(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("dirs")) {
            modifiedArgs.put("dirs", toList(modifiedArgs.get("dirs")));
        }
        FlatDirectoryArtifactRepository repository = addRepository(getResolverFactory().createFlatDirRepository(), modifiedArgs, "flatDir");
        return toResolver(FileSystemResolver.class, repository);
    }

    private String getNameFromMap(Map args, String defaultName) {
        Object name = args.get("name");
        return name != null ? name.toString() : defaultName;
    }

    private List<Object> createListFromMapArg(Map args, String argName) {
        Object dirs = args.get(argName);
        if (dirs == null) {
            return Collections.emptyList();
        }
        Iterable<Object> iterable;
        if (dirs instanceof Iterable) {
            iterable = (Iterable<Object>) dirs;
        } else {
            iterable = WrapUtil.toSet(dirs);
        }
        List<Object> list = new ArrayList<Object>();
        for (Object o : iterable) {
            list.add(o);
        }
        return list;
    }

    public DependencyResolver mavenCentral() {
        return mavenCentral(Collections.<String, Object>emptyMap());
    }

    public DependencyResolver mavenCentral(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            List<Object> urls = toList(modifiedArgs.remove("urls"));
            modifiedArgs.put("artifactUrls", urls);
        }
        MavenArtifactRepository repository = addRepository(getResolverFactory().createMavenCentralRepository(), modifiedArgs, DEFAULT_MAVEN_CENTRAL_REPO_NAME);
        return toResolver(DependencyResolver.class, repository);
    }

    public DependencyResolver mavenLocal() {
        MavenArtifactRepository repository = addRepository(getResolverFactory().createMavenLocalRepository(), DEFAULT_MAVEN_LOCAL_REPO_NAME);
        return toResolver(DependencyResolver.class, repository);
    }

    public DependencyResolver mavenRepo(Map<String, ?> args) {
        return mavenRepo(args, null);
    }

    public DependencyResolver mavenRepo(Map<String, ?> args, Closure configClosure) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("urls")) {
            List<Object> urls = toList(modifiedArgs.remove("urls"));
            if (!urls.isEmpty()) {
                modifiedArgs.put("url", urls.get(0));
                List<Object> extraUrls = urls.subList(1, urls.size());
                modifiedArgs.put("artifactUrls", extraUrls);
            }
        }

        MavenArtifactRepository repository = getResolverFactory().createMavenRepository();
        ConfigureUtil.configureByMap(modifiedArgs, repository);
        addRepository(repository, configClosure, "maven");
        return toResolver(DependencyResolver.class, repository);
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

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer(args);
        return (GroovyMavenDeployer) addLast(mavenDeployer);
    }

    private GroovyMavenDeployer createMavenDeployer(Map<String, ?> args) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer("dummyName");
        String defaultName = RepositoryHandler.DEFAULT_MAVEN_DEPLOYER_NAME + "-" + System.identityHashCode(
                mavenDeployer);
        mavenDeployer.setName(getNameFromMap(args, defaultName));
        return mavenDeployer;
    }

    public GroovyMavenDeployer mavenDeployer() {
        return mavenDeployer(Collections.<String, Object>emptyMap());
    }

    public GroovyMavenDeployer mavenDeployer(Closure configureClosure) {
        return mavenDeployer(Collections.<String, Object>emptyMap(), configureClosure);
    }

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args, Closure configureClosure) {
        GroovyMavenDeployer mavenDeployer = createMavenDeployer(args);
        return (GroovyMavenDeployer) addLast(mavenDeployer, configureClosure);
    }

    public MavenResolver mavenInstaller() {
        return mavenInstaller(Collections.<String, Object>emptyMap());
    }

    public MavenResolver mavenInstaller(Closure configureClosure) {
        return mavenInstaller(Collections.<String, Object>emptyMap(), configureClosure);
    }

    public MavenResolver mavenInstaller(Map<String, ?> args) {
        MavenResolver mavenInstaller = createMavenInstaller(args);
        return (MavenResolver) addLast(mavenInstaller);
    }

    public MavenResolver mavenInstaller(Map<String, ?> args, Closure configureClosure) {
        MavenResolver mavenInstaller = createMavenInstaller(args);
        return (MavenResolver) addLast(mavenInstaller, configureClosure);
    }

    private MavenResolver createMavenInstaller(Map<String, ?> args) {
        MavenResolver mavenInstaller = createMavenInstaller("dummyName");
        String defaultName = RepositoryHandler.DEFAULT_MAVEN_INSTALLER_NAME + "-" + System.identityHashCode(
                mavenInstaller);
        mavenInstaller.setName(getNameFromMap(args, defaultName));
        return mavenInstaller;
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

    private <T extends ArtifactRepository> T addRepository(T repository, Action<? super T> action, String defaultName) {
        action.execute(repository);
        addRepository(repository, defaultName);
        return repository;
    }

    private <T extends ArtifactRepository> T addRepository(T repository, Closure closure, String defaultName) {
        ConfigureUtil.configure(closure, repository);
        addRepository(repository, defaultName);
        return repository;
    }

    private <T extends ArtifactRepository> T addRepository(T repository, Map<String, ?> args, String defaultName) {
        ConfigureUtil.configureByMap(args, repository);
        addRepository(repository, defaultName);
        return repository;
    }

    private <T extends ArtifactRepository> T addRepository(T repository, String defaultName) {
        String repositoryName = repository.getName();
        if (!GUtil.isTrue(repositoryName)) {
            repositoryName = findName(defaultName);
            repository.setName(repositoryName);
        }
        repositoryNames.add(repositoryName);

        List<DependencyResolver> resolvers = new ArrayList<DependencyResolver>();
        ArtifactRepositoryInternal internalRepository = (ArtifactRepositoryInternal) repository;
        internalRepository.createResolvers(resolvers);
        for (DependencyResolver resolver : resolvers) {
            addLast(resolver);
        }
        return repository;
    }

    private String findName(String defaultName) {
        if (!repositoryNames.contains(defaultName)) {
            return defaultName;
        }
        for (int index = 2; true; index++) {
            String candidate = String.format("%s%d", defaultName, index);
            if (!repositoryNames.contains(candidate)) {
                return candidate;
            }
        }
    }
}
