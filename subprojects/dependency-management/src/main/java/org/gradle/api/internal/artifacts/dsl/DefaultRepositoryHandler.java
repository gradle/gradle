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
import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.ConfigureByMapAction;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.util.HashMap;
import java.util.Map;

import static org.gradle.util.CollectionUtils.flattenCollections;

public class DefaultRepositoryHandler extends DefaultArtifactRepositoryContainer implements RepositoryHandler {

    public static final String GRADLE_PLUGIN_PORTAL_REPO_NAME = "Gradle Central Plugin Repository";
    public static final String DEFAULT_BINTRAY_JCENTER_REPO_NAME = "BintrayJCenter";
    public static final String BINTRAY_JCENTER_URL = "https://jcenter.bintray.com/";
    public static final String GOOGLE_REPO_NAME = "Google";

    public static final String FLAT_DIR_DEFAULT_NAME = "flatDir";
    private static final String MAVEN_REPO_DEFAULT_NAME = "maven";
    private static final String IVY_REPO_DEFAULT_NAME = "ivy";

    private final BaseRepositoryFactory repositoryFactory;

    public DefaultRepositoryHandler(BaseRepositoryFactory repositoryFactory, Instantiator instantiator, CollectionCallbackActionDecorator decorator) {
        super(instantiator, decorator);
        this.repositoryFactory = repositoryFactory;
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return addRepository(repositoryFactory.createFlatDirRepository(), FLAT_DIR_DEFAULT_NAME, action);
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return flatDir(ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        if (modifiedArgs.containsKey("dirs")) {
            modifiedArgs.put("dirs", flattenCollections(modifiedArgs.get("dirs")));
        }
        return flatDir(new ConfigureByMapAction<FlatDirectoryArtifactRepository>(modifiedArgs));
    }

    @Override
    public ArtifactRepository gradlePluginPortal() {
        return addRepository(repositoryFactory.createGradlePluginPortal(), GRADLE_PLUGIN_PORTAL_REPO_NAME);
    }
    
    @Override
    public ArtifactRepository gradlePluginPortal(Action<? super ArtifactRepository> action) {
        return addRepository(repositoryFactory.createGradlePluginPortal(), GRADLE_PLUGIN_PORTAL_REPO_NAME, action);
    }

    @Override
    public MavenArtifactRepository mavenCentral() {
        return addRepository(repositoryFactory.createMavenCentralRepository(), DEFAULT_MAVEN_CENTRAL_REPO_NAME);
    }

    @Override
    public MavenArtifactRepository mavenCentral(Action<? super MavenArtifactRepository> action) {
        return addRepository(repositoryFactory.createMavenCentralRepository(), DEFAULT_MAVEN_CENTRAL_REPO_NAME, action);
    }

    @Override
    public MavenArtifactRepository jcenter() {
        return addRepository(repositoryFactory.createJCenterRepository(), DEFAULT_BINTRAY_JCENTER_REPO_NAME);
    }

    @Override
    public MavenArtifactRepository jcenter(Action<? super MavenArtifactRepository> action) {
        return addRepository(repositoryFactory.createJCenterRepository(), DEFAULT_BINTRAY_JCENTER_REPO_NAME, action);
    }

    @Override
    public MavenArtifactRepository mavenCentral(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<String, Object>(args);
        return addRepository(repositoryFactory.createMavenCentralRepository(), DEFAULT_MAVEN_CENTRAL_REPO_NAME, new ConfigureByMapAction<MavenArtifactRepository>(modifiedArgs));
    }

    @Override
    public MavenArtifactRepository mavenLocal() {
        return addRepository(repositoryFactory.createMavenLocalRepository(), DEFAULT_MAVEN_LOCAL_REPO_NAME);
    }

    @Override
    public MavenArtifactRepository mavenLocal(Action<? super MavenArtifactRepository> action) {
        return addRepository(repositoryFactory.createMavenLocalRepository(), DEFAULT_MAVEN_LOCAL_REPO_NAME, action);
    }

    @Override
    public MavenArtifactRepository google() {
        return addRepository(repositoryFactory.createGoogleRepository(), GOOGLE_REPO_NAME);
    }

    @Override
    public MavenArtifactRepository google(Action<? super MavenArtifactRepository> action) {
        return addRepository(repositoryFactory.createGoogleRepository(), GOOGLE_REPO_NAME, action);
    }

    @Override
    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        return addRepository(repositoryFactory.createMavenRepository(), MAVEN_REPO_DEFAULT_NAME, action);
    }

    @Override
    public MavenArtifactRepository maven(Closure closure) {
        return maven(ConfigureUtil.configureUsing(closure));
    }

    @Override
    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return addRepository(repositoryFactory.createIvyRepository(), IVY_REPO_DEFAULT_NAME, action);
    }

    @Override
    public IvyArtifactRepository ivy(Closure closure) {
        return ivy(ConfigureUtil.configureUsing(closure));
    }
}
