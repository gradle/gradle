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

package org.gradle.configuration;

import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.internal.buildtree.BuildInclusionCoordinator;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;

public class BuildTreePreparingProjectsPreparer implements ProjectsPreparer {
    private final ProjectsPreparer delegate;
    private final BuildInclusionCoordinator coordinator;
    private final BuildSourceBuilder buildSourceBuilder;
    private final BuildLoader buildLoader;

    public BuildTreePreparingProjectsPreparer(ProjectsPreparer delegate, BuildLoader buildLoader, BuildInclusionCoordinator coordinator, BuildSourceBuilder buildSourceBuilder) {
        this.delegate = delegate;
        this.buildLoader = buildLoader;
        this.coordinator = coordinator;
        this.buildSourceBuilder = buildSourceBuilder;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        // Setup classloader for root project, all other projects will be derived from this.
        SettingsInternal settings = gradle.getSettings();
        ClassLoaderScope settingsClassLoaderScope = settings.getClassLoaderScope();
        ClassLoaderScope buildSrcClassLoaderScope = settingsClassLoaderScope.createChild("buildSrc[" + gradle.getIdentityPath() + "]");
        gradle.setBaseProjectClassLoaderScope(buildSrcClassLoaderScope);
        generateDependenciesAccessorsAndAssignPluginVersions(gradle.getServices(), settings, buildSrcClassLoaderScope);
        // attaches root project
        buildLoader.load(gradle.getSettings(), gradle);
        // Makes included build substitutions available
        if (gradle.isRootBuild()) {
            coordinator.registerGlobalLibrarySubstitutions();
        }
        // Build buildSrc and export classpath to root project
        buildBuildSrcAndLockClassloader(gradle, buildSrcClassLoaderScope);

        delegate.prepareProjects(gradle);
    }

    private void buildBuildSrcAndLockClassloader(GradleInternal gradle, ClassLoaderScope baseProjectClassLoaderScope) {
        ClassPath buildSrcClassPath = buildSourceBuilder.buildAndGetClassPath(gradle);
        baseProjectClassLoaderScope.export(buildSrcClassPath).lock();
    }

    private void generateDependenciesAccessorsAndAssignPluginVersions(ServiceRegistry services, SettingsInternal settings, ClassLoaderScope classLoaderScope) {
        DependenciesAccessors accessors = services.get(DependenciesAccessors.class);
        DependencyResolutionManagementInternal dm = services.get(DependencyResolutionManagementInternal.class);
        dm.getDefaultLibrariesExtensionName().finalizeValue();
        String defaultLibrary = dm.getDefaultLibrariesExtensionName().get();
        File dependenciesFile = new File(settings.getSettingsDir(), "gradle/libs.versions.toml");
        if (dependenciesFile.exists()) {
            dm.versionCatalogs(catalogs -> {
                VersionCatalogBuilder builder = catalogs.findByName(defaultLibrary);
                if (builder == null) {
                    builder = catalogs.create(defaultLibrary);
                }
                builder.from(services.get(FileCollectionFactory.class).fixed(dependenciesFile));
            });
        }
        accessors.generateAccessors(dm.getDependenciesModelBuilders(), classLoaderScope, settings);
    }
}
