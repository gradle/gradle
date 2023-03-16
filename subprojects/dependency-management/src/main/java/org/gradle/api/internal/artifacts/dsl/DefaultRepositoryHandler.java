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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.ExclusiveContentRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.InclusiveRepositoryContentDescriptor;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.ConfigureByMapAction;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.internal.ConfigureUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.util.internal.CollectionUtils.flattenCollections;

public class DefaultRepositoryHandler extends DefaultArtifactRepositoryContainer implements RepositoryHandlerInternal {

    public static final String GRADLE_PLUGIN_PORTAL_REPO_NAME = "Gradle Central Plugin Repository";
    public static final String DEFAULT_BINTRAY_JCENTER_REPO_NAME = "BintrayJCenter";
    public static final String BINTRAY_JCENTER_URL = "https://jcenter.bintray.com/";
    public static final String GOOGLE_REPO_NAME = "Google";

    public static final String FLAT_DIR_DEFAULT_NAME = "flatDir";
    private static final String MAVEN_REPO_DEFAULT_NAME = "maven";
    private static final String IVY_REPO_DEFAULT_NAME = "ivy";

    private final BaseRepositoryFactory repositoryFactory;
    private final Instantiator instantiator;
    private boolean exclusiveContentInUse = false;

    public DefaultRepositoryHandler(BaseRepositoryFactory repositoryFactory, Instantiator instantiator, CollectionCallbackActionDecorator decorator) {
        super(instantiator, decorator);
        this.repositoryFactory = repositoryFactory;
        this.instantiator = instantiator;
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return addRepository(repositoryFactory.createFlatDirRepository(), FLAT_DIR_DEFAULT_NAME, action);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return flatDir(ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<>(args);
        if (modifiedArgs.containsKey("dirs")) {
            modifiedArgs.put("dirs", flattenCollections(modifiedArgs.get("dirs")));
        }
        return flatDir(new ConfigureByMapAction<>(modifiedArgs));
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

    @Deprecated
    @Override
    public MavenArtifactRepository jcenter() {
        deprecateJCenter("jcenter()", "mavenCentral()");
        return addRepository(repositoryFactory.createJCenterRepository(), DEFAULT_BINTRAY_JCENTER_REPO_NAME);
    }

    @Deprecated
    @Override
    public MavenArtifactRepository jcenter(Action<? super MavenArtifactRepository> action) {
        deprecateJCenter("jcenter(Action<MavenArtifactRepository>)", "mavenCentral(Action<MavenArtifactRepository>");
        return addRepository(repositoryFactory.createJCenterRepository(), DEFAULT_BINTRAY_JCENTER_REPO_NAME, action);
    }

    private void deprecateJCenter(String method, String replacement) {
        DeprecationLogger.deprecateMethod(RepositoryHandler.class, method)
            .withAdvice("JFrog announced JCenter's sunset in February 2021. Use " + replacement + " instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(6, "jcenter_deprecation")
            .nagUser();
    }

    @Override
    public MavenArtifactRepository mavenCentral(Map<String, ?> args) {
        Map<String, Object> modifiedArgs = new HashMap<>(args);
        return addRepository(repositoryFactory.createMavenCentralRepository(), DEFAULT_MAVEN_CENTRAL_REPO_NAME, new ConfigureByMapAction<>(modifiedArgs));
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
    @SuppressWarnings("rawtypes")
    public MavenArtifactRepository maven(Closure closure) {
        return maven(ConfigureUtil.configureUsing(closure));
    }

    @Override
    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return addRepository(repositoryFactory.createIvyRepository(), IVY_REPO_DEFAULT_NAME, action);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public IvyArtifactRepository ivy(Closure closure) {
        return ivy(ConfigureUtil.configureUsing(closure));
    }

    @Override
    public void exclusiveContent(Action<? super ExclusiveContentRepository> action) {
        ExclusiveContentRepositorySpec spec = Cast.uncheckedCast(instantiator.newInstance(ExclusiveContentRepositorySpec.class, this));
        spec.apply(action);
        exclusiveContentInUse = true;
    }

    @Override
    public boolean isExclusiveContentInUse() {
        return exclusiveContentInUse;
    }

    private static Action<? super RepositoryContentDescriptor> transformForExclusivity(Action<? super InclusiveRepositoryContentDescriptor> config) {
        return desc -> config.execute(new InclusiveRepositoryContentDescriptor() {
            @Override
            public void includeGroup(String group) {
                desc.excludeGroup(group);
            }

            @Override
            public void includeGroupAndSubgroups(String groupPrefix) {
                desc.includeGroupAndSubgroups(groupPrefix);
            }

            @Override
            public void includeGroupByRegex(String groupRegex) {
                desc.excludeGroupByRegex(groupRegex);
            }

            @Override
            public void includeModule(String group, String moduleName) {
                desc.excludeModule(group, moduleName);
            }

            @Override
            public void includeModuleByRegex(String groupRegex, String moduleNameRegex) {
                desc.excludeModuleByRegex(groupRegex, moduleNameRegex);
            }

            @Override
            public void includeVersion(String group, String moduleName, String version) {
                desc.excludeVersion(group, moduleName, version);
            }

            @Override
            public void includeVersionByRegex(String groupRegex, String moduleNameRegex, String versionRegex) {
                desc.excludeVersionByRegex(groupRegex, moduleNameRegex, versionRegex);
            }
        });
    }

    public static class ExclusiveContentRepositorySpec implements ExclusiveContentRepository {
        private final RepositoryHandler repositories;
        private final List<Factory<? extends ArtifactRepository>> forRepositories = Lists.newArrayListWithExpectedSize(2);
        private Action<? super InclusiveRepositoryContentDescriptor> filter;

        public ExclusiveContentRepositorySpec(RepositoryHandler repositories) {
            this.repositories = repositories;
        }

        @Override
        public ExclusiveContentRepository forRepository(Factory<? extends ArtifactRepository> repositoryProducer) {
            forRepositories.add(repositoryProducer);
            return this;
        }

        @Override
        public ExclusiveContentRepository forRepositories(ArtifactRepository... repositories) {
            Stream.of(repositories).forEach(r -> forRepositories.add(() -> r));
            return this;
        }

        @Override
        public ExclusiveContentRepository filter(Action<? super InclusiveRepositoryContentDescriptor> config) {
            filter = filter == null ? config : Actions.composite(filter, config);
            return this;
        }

        void apply(Action<? super ExclusiveContentRepository> action) {
            action.execute(this);
            if (forRepositories.isEmpty()) {
                throw new InvalidUserCodeException("You must declare the repository using forRepository { ... }");
            }
            if (filter == null) {
                throw new InvalidUserCodeException("You must specify the filter for the repository using filter { ... }");
            }
            Set<? extends ArtifactRepository> targetRepositories = forRepositories.stream().map(Factory::create).collect(Collectors.toSet());
            Action<? super RepositoryContentDescriptor> forExclusivity = transformForExclusivity(filter);
            this.repositories.all(repo -> {
                if (targetRepositories.contains(repo)) {
                    repo.content(filter);
                } else {
                    repo.content(forExclusivity);
                }
            });
        }
    }

}
