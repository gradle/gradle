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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.ExclusiveContentRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.resolve.RepositoryRegistrar;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.initialization.resolve.RulesMode;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultDependencyResolutionManagement implements DependencyResolutionManagementInternal {
    private static final DisplayName UNKNOWN_CODE = Describables.of("unknown code");
    private static final Logger LOGGER = Logging.getLogger(DependencyResolutionManagement.class);
    private final List<Action<? super ComponentMetadataHandler>> componentMetadataRulesActions = Lists.newArrayList();
    private final List<Action<? super RepositoryHandler>> repositoriesActions = Lists.newArrayList();

    private final UserCodeApplicationContext context;
    private final ComponentMetadataHandler componentMetadataRulesRegistar = new ComponentMetadataRulesRegistar();
    private final DefaultRepositoriesRegistrar repositoriesRegistrar = new DefaultRepositoriesRegistrar();
    private final Property<RepositoriesMode> repositoryMode;
    private final Property<RulesMode> rulesMode;
    private boolean mutable = true;

    public DefaultDependencyResolutionManagement(UserCodeApplicationContext context, ObjectFactory objects) {
        this.context = context;
        this.repositoryMode = objects.property(RepositoriesMode.class).convention(RepositoriesMode.PREFER_PROJECT);
        this.rulesMode = objects.property(RulesMode.class).convention(RulesMode.PREFER_PROJECT);
    }

    @Override
    public void repositories(Action<? super RepositoryHandler> repositoryConfiguration) {
        assertMutable();
        repositoriesActions.add(repositoryConfiguration);
    }

    @Override
    public void components(Action<? super ComponentMetadataHandler> registration) {
        assertMutable();
        componentMetadataRulesActions.add(registration);
    }

    @Override
    public ComponentMetadataHandler getComponents() {
        return componentMetadataRulesRegistar;
    }

    @Override
    public RepositoryRegistrar getRepositories() {
        return repositoriesRegistrar;
    }

    @Override
    public Property<RepositoriesMode> getRepositoriesMode() {
        return repositoryMode;
    }

    @Override
    public boolean containsRepositoryActions() {
        return !repositoriesActions.isEmpty();
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
    public RulesModeInternal getConfiguredRulesMode() {
        rulesMode.finalizeValue();
        return RulesModeInternal.of(rulesMode.get());
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
    }

    private void assertMutable() {
        if (!mutable) {
            throw new InvalidUserCodeException("Mutation of dependency resolution management in settings is only allowed during settings evaluation");
        }
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

    @Override
    public void applyComponentMetadataRules(ComponentMetadataHandler target) {
        for (Action<? super ComponentMetadataHandler> rule : componentMetadataRulesActions) {
            rule.execute(target);
        }
    }

    @Override
    public void applyRepositoryRules(RepositoryHandler target) {
        for (Action<? super RepositoryHandler> action : repositoriesActions) {
            action.execute(target);
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

    private class DefaultRepositoriesRegistrar implements RepositoryRegistrar {

        @Override
        public RepositoryRegistrar flatDir(Map<String, ?> args) {
            repositories(h -> h.flatDir(args));
            return this;
        }

        @Override
        public RepositoryRegistrar flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
            repositories(h -> h.flatDir(action));
            return this;
        }

        @Override
        public RepositoryRegistrar gradlePluginPortal() {
            repositories(RepositoryHandler::gradlePluginPortal);
            return this;
        }

        @Override
        public RepositoryRegistrar gradlePluginPortal(Action<? super ArtifactRepository> action) {
            repositories(h -> h.gradlePluginPortal(action));
            return this;
        }

        @Override
        public RepositoryRegistrar jcenter(Action<? super MavenArtifactRepository> action) {
            repositories(h -> h.jcenter(action));
            return this;
        }

        @Override
        public RepositoryRegistrar jcenter() {
            repositories(RepositoryHandler::jcenter);
            return this;
        }

        @Override
        public RepositoryRegistrar mavenCentral(Map<String, ?> args) {
            repositories(h -> h.mavenCentral(args));
            return this;
        }

        @Override
        public RepositoryRegistrar mavenCentral() {
            repositories(RepositoryHandler::mavenCentral);
            return this;
        }

        @Override
        public RepositoryRegistrar mavenCentral(Action<? super MavenArtifactRepository> action) {
            repositories(h -> h.mavenCentral(action));
            return this;
        }

        @Override
        public RepositoryRegistrar mavenLocal() {
            repositories(RepositoryHandler::mavenLocal);
            return this;
        }

        @Override
        public RepositoryRegistrar mavenLocal(Action<? super MavenArtifactRepository> action) {
            repositories(h -> h.mavenLocal(action));
            return this;
        }

        @Override
        public RepositoryRegistrar google() {
            repositories(RepositoryHandler::google);
            return this;
        }

        @Override
        public RepositoryRegistrar google(Action<? super MavenArtifactRepository> action) {
            repositories(h -> h.google(action));
            return this;
        }

        @Override
        public RepositoryRegistrar maven(Action<? super MavenArtifactRepository> action) {
            repositories(h -> h.maven(action));
            return this;
        }

        @Override
        public RepositoryRegistrar ivy(Action<? super IvyArtifactRepository> action) {
            repositories(h -> h.ivy(action));
            return this;
        }

        @Override
        public RepositoryRegistrar exclusiveContent(Action<? super ExclusiveContentRepository> action) {
            repositories(h -> h.exclusiveContent(action));
            return this;
        }
    }
}
