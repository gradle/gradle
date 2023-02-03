/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.management;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.initialization.resolve.RulesMode;
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.lazy.Lazy;

import java.util.List;

@NonNullApi
public class DefaultDependencyResolutionManagement implements DependencyResolutionManagementInternal {
    private static final DisplayName UNKNOWN_CODE = Describables.of("unknown code");
    private static final Logger LOGGER = Logging.getLogger(DependencyResolutionManagement.class);
    private final List<Action<? super ComponentMetadataHandler>> componentMetadataRulesActions = Lists.newArrayList();

    private final Lazy<DependencyResolutionServices> dependencyResolutionServices;
    private final UserCodeApplicationContext context;
    private final ComponentMetadataHandler registar = new ComponentMetadataRulesRegistar();
    private final Property<RepositoriesMode> repositoryMode;
    private final Property<RulesMode> rulesMode;
    private final Property<String> librariesExtensionName;
    private final Property<String> projectsExtensionName;
    private final DefaultVersionCatalogBuilderContainer versionCatalogs;

    private boolean mutable = true;

    public DefaultDependencyResolutionManagement(UserCodeApplicationContext context,
                                                 DependencyManagementServices dependencyManagementServices,
                                                 FileResolver fileResolver,
                                                 FileCollectionFactory fileCollectionFactory,
                                                 DependencyMetaDataProvider dependencyMetaDataProvider,
                                                 ObjectFactory objects,
                                                 ProviderFactory providers,
                                                 CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.context = context;
        this.repositoryMode = objects.property(RepositoriesMode.class).convention(RepositoriesMode.PREFER_PROJECT);
        this.rulesMode = objects.property(RulesMode.class).convention(RulesMode.PREFER_PROJECT);
        this.dependencyResolutionServices = Lazy.locking().of(() -> dependencyManagementServices.create(fileResolver, fileCollectionFactory, dependencyMetaDataProvider, makeUnknownProjectFinder(), RootScriptDomainObjectContext.INSTANCE));
        this.librariesExtensionName = objects.property(String.class).convention("libs");
        this.projectsExtensionName = objects.property(String.class).convention("projects");
        this.versionCatalogs = objects.newInstance(DefaultVersionCatalogBuilderContainer.class, collectionCallbackActionDecorator, objects, providers, dependencyResolutionServices, context);
    }

    @Override
    public void repositories(Action<? super RepositoryHandler> repositoryConfiguration) {
        repositoryConfiguration.execute(dependencyResolutionServices.get().getResolveRepositoryHandler());
    }

    @Override
    public void components(Action<? super ComponentMetadataHandler> registration) {
        assertMutable();
        componentMetadataRulesActions.add(registration);
    }

    @Override
    public ComponentMetadataHandler getComponents() {
        return registar;
    }

    @Override
    public RepositoryHandler getRepositories() {
        return dependencyResolutionServices.get().getResolveRepositoryHandler();
    }

    @Override
    public Property<RepositoriesMode> getRepositoriesMode() {
        return repositoryMode;
    }

    @Override
    public Property<RulesMode> getRulesMode() {
        return rulesMode;
    }

    @Override
    public RepositoriesModeInternal getConfiguredRepositoriesMode() {
        repositoryMode.finalizeValue();
        return RepositoriesModeInternal.of(repositoryMode.get());
    }

    @Override
    public void versionCatalogs(Action<? super MutableVersionCatalogContainer> spec) {
        spec.execute(versionCatalogs);
    }

    @Override
    public MutableVersionCatalogContainer getVersionCatalogs() {
        return versionCatalogs;
    }

    @Override
    public RulesModeInternal getConfiguredRulesMode() {
        rulesMode.finalizeValue();
        return RulesModeInternal.of(rulesMode.get());
    }

    @Override
    public Property<String> getDefaultProjectsExtensionName() {
        return projectsExtensionName;
    }

    @Override
    public Property<String> getDefaultLibrariesExtensionName() {
        return librariesExtensionName;
    }

    @Override
    public List<VersionCatalogBuilder> getDependenciesModelBuilders() {
        return ImmutableList.copyOf(versionCatalogs);
    }

    @Override
    public void configureProject(ProjectInternal project) {
        if (!getConfiguredRepositoriesMode().useProjectRepositories()) {
            project.getRepositories().whenObjectAdded(this::repoMutationDisallowedOnProject);
        }
        if (!getConfiguredRulesMode().useProjectRules()) {
            ComponentMetadataHandlerInternal components = (ComponentMetadataHandlerInternal) project.getDependencies().getComponents();
            components.onAddRule(this::ruleMutationDisallowedOnProject);
        }
    }

    @Override
    public void preventFromFurtherMutation() {
        this.mutable = false;
        NamedDomainObjectList<ArtifactRepository> repositoryHandler = getRepositories();
        repositoryHandler.whenObjectAdded(this::mutationDisallowed);
        repositoryHandler.whenObjectRemoved(this::mutationDisallowed);
    }

    private void assertMutable() {
        if (!mutable) {
            throw new InvalidUserCodeException("Mutation of dependency resolution management in settings is only allowed during settings evaluation");
        }
    }

    private void mutationDisallowed(ArtifactRepository artifactRepository) {
        throw new InvalidUserCodeException("Mutation of repositories declared in settings is only allowed during settings evaluation");
    }

    private void repoMutationDisallowedOnProject(ArtifactRepository artifactRepository) {
        UserCodeApplicationContext.Application current = context.current();
        DisplayName displayName = current == null ? null : current.getDisplayName();
        if (displayName == null) {
            displayName = UNKNOWN_CODE;
        }
        String message = "Build was configured to prefer settings repositories over project repositories but repository '" + artifactRepository.getName() + "' was added by " + displayName;
        switch (getConfiguredRepositoriesMode()) {
            case FAIL_ON_PROJECT_REPOS:
                throw new InvalidUserCodeException(message);
            case PREFER_SETTINGS:
                LOGGER.warn(message);
                break;
        }
    }

    private void ruleMutationDisallowedOnProject(DisplayName ruleName) {
        UserCodeApplicationContext.Application current = context.current();
        DisplayName displayName = current == null ? null : current.getDisplayName();
        if (displayName == null) {
            displayName = UNKNOWN_CODE;
        }
        String message = "Build was configured to prefer settings component metadata rules over project rules but rule '" + ruleName + "' was added by " + displayName;
        switch (getConfiguredRulesMode()) {
            case FAIL_ON_PROJECT_RULES:
                throw new InvalidUserCodeException(message);
            case PREFER_SETTINGS:
                LOGGER.warn(message);
                break;
        }
    }

    private static ProjectFinder makeUnknownProjectFinder() {
        return new UnknownProjectFinder("Project dependencies are not allowed in shared dependency resolution services");
    }

    @Override
    public void applyRules(ComponentMetadataHandler target) {
        for (Action<? super ComponentMetadataHandler> rule : componentMetadataRulesActions) {
            rule.execute(target);
        }
    }

    private class ComponentMetadataRulesRegistar implements ComponentMetadataHandler {
        @Override
        public ComponentMetadataHandler all(Action<? super ComponentMetadataDetails> rule) {
            components(h -> h.all(rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Closure<?> rule) {
            components(h -> h.all(rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Object ruleSource) {
            components(h -> h.all(ruleSource));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Class<? extends ComponentMetadataRule> rule) {
            components(h -> h.all(rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler all(Class<? extends ComponentMetadataRule> rule, Action<? super ActionConfiguration> configureAction) {
            components(h -> h.all(rule, configureAction));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Action<? super ComponentMetadataDetails> rule) {
            components(h -> h.withModule(id, rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Closure<?> rule) {
            components(h -> h.withModule(id, rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Object ruleSource) {
            components(h -> h.withModule(id, ruleSource));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Class<? extends ComponentMetadataRule> rule) {
            components(h -> h.withModule(id, rule));
            return this;
        }

        @Override
        public ComponentMetadataHandler withModule(Object id, Class<? extends ComponentMetadataRule> rule, Action<? super ActionConfiguration> configureAction) {
            components(h -> h.withModule(id, rule, configureAction));
            return this;
        }
    }
}
