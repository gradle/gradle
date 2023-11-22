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
package org.gradle.api.internal.initialization;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.util.internal.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;

import static java.lang.Boolean.getBoolean;

@NonExtensible
public class DefaultScriptHandler implements ScriptHandler, ScriptHandlerInternal {

    /**
     * If set to {@code true}, the buildscript's {@code classpath} configuration will not be reset after the
     * classpath has been assembled. Defaults to {@code false}.
     */
    public static final String DISABLE_RESET_CONFIGURATION_SYSTEM_PROPERTY = "org.gradle.incubating.reset-buildscript-classpath.disabled";

    private static final Logger LOGGER = Logging.getLogger(DefaultScriptHandler.class);

    private final ResourceLocation scriptResource;
    private final ClassLoaderScope classLoaderScope;
    private final DependencyResolutionServices dependencyResolutionServices;
    private final DependencyLockingHandler dependencyLockingHandler;
    private final BuildLogicBuilder buildLogicBuilder;
    // The following values are relatively expensive to create, so defer creation until required
    private ClassPath resolvedClasspath;
    private RepositoryHandler repositoryHandler;
    private DependencyHandler dependencyHandler;
    private RoleBasedConfigurationContainerInternal configContainer;
    private Configuration classpathConfiguration;

    @Inject
    public DefaultScriptHandler(
        ScriptSource scriptSource,
        DependencyResolutionServices dependencyResolutionServices,
        ClassLoaderScope classLoaderScope,
        BuildLogicBuilder buildLogicBuilder
    ) {
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.scriptResource = scriptSource.getResource().getLocation();
        this.classLoaderScope = classLoaderScope;
        this.dependencyLockingHandler = dependencyResolutionServices.getDependencyLockingHandler();
        this.buildLogicBuilder = buildLogicBuilder;
        JavaEcosystemSupport.configureSchema(dependencyResolutionServices.getAttributesSchema(), dependencyResolutionServices.getObjectFactory());
    }

    @Override
    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getDependencies());
    }

    @Override
    public void addScriptClassPathDependency(Object notation) {
        getDependencies().add(ScriptHandler.CLASSPATH_CONFIGURATION, notation);
    }

    @Override
    public ClassPath getScriptClassPath() {
        return ClasspathUtil.getClasspath(getClassLoader());
    }

    @Override
    public ClassPath getInstrumentedScriptClassPath() {
        if (resolvedClasspath == null) {
            if (classpathConfiguration != null) {
                resolvedClasspath = buildLogicBuilder.resolveClassPath(classpathConfiguration, dependencyHandler, configContainer);
                if (!getBoolean(DISABLE_RESET_CONFIGURATION_SYSTEM_PROPERTY)) {
                    ((ResettableConfiguration) classpathConfiguration).resetResolutionState();
                }
            } else {
                resolvedClasspath = ClassPath.EMPTY;
            }
        }
        return resolvedClasspath;
    }

    public void dropResolvedClassPath() {
        resolvedClasspath = null;
    }

    @Override
    public DependencyHandler getDependencies() {
        defineConfiguration();
        return dependencyHandler;
    }

    @Override
    public RepositoryHandler getRepositories() {
        if (repositoryHandler == null) {
            repositoryHandler = dependencyResolutionServices.getResolveRepositoryHandler();
        }
        return repositoryHandler;
    }

    @Override
    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRepositories());
    }

    @Override
    public ConfigurationContainer getConfigurations() {
        defineConfiguration();
        return configContainer;
    }

    @Override
    public void configurations(Action<? super ConfigurationContainer> configureClosure) {
        configureClosure.execute(getConfigurations());
    }

    @SuppressWarnings("deprecation")
    private void defineConfiguration() {
        // Defer creation and resolution of configuration until required. Short-circuit when script does not require classpath
        if (configContainer == null) {
            configContainer = (RoleBasedConfigurationContainerInternal) dependencyResolutionServices.getConfigurationContainer();
        }
        if (dependencyHandler == null) {
            dependencyHandler = getAndConfigureDependencyHandler(dependencyResolutionServices);
        }
        if (classpathConfiguration == null) {
            classpathConfiguration = configContainer.migratingUnlocked(CLASSPATH_CONFIGURATION, ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE);
            buildLogicBuilder.prepareClassPath(classpathConfiguration, dependencyHandler);
        }
    }

    private static DependencyHandler getAndConfigureDependencyHandler(DependencyResolutionServices dependencyResolutionServices) {
        // TODO: JavaEcosystemSupport.configureSchema is called in the constructor, should we move it here?
        DependencyHandler dependencyHandler = dependencyResolutionServices.getDependencyHandler();
        dependencyHandler.getArtifactTypes().create(ArtifactTypeDefinition.JAR_TYPE);
        dependencyHandler.getArtifactTypes().create(ArtifactTypeDefinition.DIRECTORY_TYPE);
        return dependencyHandler;
    }

    @Override
    public void dependencyLocking(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getDependencyLocking());
    }

    @Override
    public DependencyLockingHandler getDependencyLocking() {
        return dependencyLockingHandler;
    }

    @Override
    public File getSourceFile() {
        return scriptResource.getFile();
    }

    @Override
    public URI getSourceURI() {
        return scriptResource.getURI();
    }

    @Override
    public ClassLoader getClassLoader() {
        if (!classLoaderScope.isLocked()) {
            LOGGER.debug("Eager creation of script class loader for {}. This may result in performance issues.", scriptResource.getDisplayName());
        }
        return classLoaderScope.getLocalClassLoader();
    }
}
