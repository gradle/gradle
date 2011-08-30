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
import org.gradle.api.artifacts.dsl.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.dsl.IvyArtifactRepository;
import org.gradle.api.artifacts.dsl.MavenArtifactRepository;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.repositories.FixedResolverArtifactRepository;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandler extends DefaultArtifactRepositoryContainer implements RepositoryHandler {
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

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args) {
        return addRepository(createMavenDeployer(), args, DEFAULT_MAVEN_DEPLOYER_NAME);
    }

    private GroovyMavenDeployer createMavenDeployer() {
        return getResolverFactory().createMavenDeployer(this, getConfigurationContainer(), getMavenScopeMappings(), getFileResolver());
    }

    public GroovyMavenDeployer mavenDeployer() {
        return addRepository(createMavenDeployer(), DEFAULT_MAVEN_DEPLOYER_NAME);
    }

    public GroovyMavenDeployer mavenDeployer(Closure configureClosure) {
        return addRepository(createMavenDeployer(), configureClosure, DEFAULT_MAVEN_DEPLOYER_NAME);
    }

    public GroovyMavenDeployer mavenDeployer(Map<String, ?> args, Closure configureClosure) {
        GroovyMavenDeployer deployer = createMavenDeployer();
        ConfigureUtil.configureByMap(args, deployer);
        return addRepository(deployer, configureClosure, DEFAULT_MAVEN_DEPLOYER_NAME);
    }

    public MavenResolver mavenInstaller() {
        return addRepository(createMavenInstaller(), DEFAULT_MAVEN_INSTALLER_NAME);
    }

    public MavenResolver mavenInstaller(Closure configureClosure) {
        return addRepository(createMavenInstaller(), configureClosure, DEFAULT_MAVEN_INSTALLER_NAME);
    }

    public MavenResolver mavenInstaller(Map<String, ?> args) {
        return addRepository(createMavenInstaller(), args, DEFAULT_MAVEN_INSTALLER_NAME);
    }

    public MavenResolver mavenInstaller(Map<String, ?> args, Closure configureClosure) {
        MavenResolver installer = createMavenInstaller();
        ConfigureUtil.configureByMap(args, installer);
        return addRepository(installer, configureClosure, DEFAULT_MAVEN_INSTALLER_NAME);
    }

    private MavenResolver createMavenInstaller() {
        return getResolverFactory().createMavenInstaller(this, getConfigurationContainer(), getMavenScopeMappings(), getFileResolver());
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
}
