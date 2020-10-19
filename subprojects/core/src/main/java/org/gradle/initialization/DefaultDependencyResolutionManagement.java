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
package org.gradle.initialization;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.initialization.DependencyResolutionManagement;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.lazy.Lazy;

public class DefaultDependencyResolutionManagement implements DependencyResolutionManagementInternal {
    private static final DisplayName UNKNOWN_CODE = Describables.of("unknown code");
    private static final Logger LOGGER = Logging.getLogger(DependencyResolutionManagement.class);

    private final Lazy<DependencyResolutionServices> dependencyResolutionServices;
    private final UserCodeApplicationContext context;
    private boolean mutable = true;
    private RepositoryMode repositoryMode = RepositoryMode.PREFER_PROJECT;

    public DefaultDependencyResolutionManagement(UserCodeApplicationContext context,
                                                 DependencyManagementServices dependencyManagementServices,
                                                 FileResolver fileResolver,
                                                 FileCollectionFactory fileCollectionFactory,
                                                 DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.context = context;
        this.dependencyResolutionServices = Lazy.locking().of(() -> dependencyManagementServices.create(fileResolver, fileCollectionFactory, dependencyMetaDataProvider, makeUnknownProjectFinder(), RootScriptDomainObjectContext.INSTANCE));
    }

    @Override
    public void repositories(Action<? super RepositoryHandler> repositoryConfiguration) {
        repositoryConfiguration.execute(dependencyResolutionServices.get().getResolveRepositoryHandler());
    }

    @Override
    public RepositoryHandler getRepositoryHandler() {
        return dependencyResolutionServices.get().getResolveRepositoryHandler();
    }

    @Override
    public RepositoryMode getRepositoryMode() {
        return repositoryMode;
    }

    @Override
    public void configureProject(ProjectInternal project) {
        if (!repositoryMode.useProjectRepositories()) {
            project.getRepositories().whenObjectAdded(this::mutationDisallowedOnProject);
        }
    }

    @Override
    public void preventFromFurtherMutation() {
        this.mutable = false;
        NamedDomainObjectList<ArtifactRepository> repositoryHandler = getRepositoryHandler();
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

    private void mutationDisallowedOnProject(ArtifactRepository artifactRepository) {
        UserCodeApplicationContext.Application current = context.current();
        DisplayName displayName = current == null ? null : current.getDisplayName();
        if (displayName == null) {
            displayName = UNKNOWN_CODE;
        }
        String message = "Build was configured to prefer settings repositories over project repositories but repository '" + artifactRepository.getName() + "' was added by " + displayName;
        switch (repositoryMode) {
            case FAIL_ON_PROJECT_REPOS:
                throw new InvalidUserCodeException(message);
            case PREFER_SETTINGS:
                LOGGER.warn(message);
                break;
        }
    }

    @Override
    public void preferProjectRepositories() {
        assertMutable();
        repositoryMode = RepositoryMode.PREFER_PROJECT;
    }

    @Override
    public void preferSettingsRepositories() {
        assertMutable();
        repositoryMode = RepositoryMode.PREFER_SETTINGS;
    }

    @Override
    public void enforceSettingsRepositories() {
        assertMutable();
        repositoryMode = RepositoryMode.FAIL_ON_PROJECT_REPOS;
    }

    private static ProjectFinder makeUnknownProjectFinder() {
        return new UnknownProjectFinder("Project dependencies are not allowed in shared dependency resolution services");
    }
}
