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
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.net.URI;

public class DefaultScriptHandler implements ScriptHandler, ScriptHandlerInternal, DynamicObjectAware {
    private static final Logger LOGGER = Logging.getLogger(DefaultScriptHandler.class);

    private final ResourceLocation scriptResource;
    private final ClassLoaderScope classLoaderScope;
    private final ScriptClassPathResolver scriptClassPathResolver;
    private final DependencyResolutionServices dependencyResolutionServices;
    private final NamedObjectInstantiator instantiator;
    private final DependencyLockingHandler dependencyLockingHandler;
    // The following values are relatively expensive to create, so defer creation until required
    private RepositoryHandler repositoryHandler;
    private DependencyHandler dependencyHandler;
    private ConfigurationContainer configContainer;
    private Configuration classpathConfiguration;
    private DynamicObject dynamicObject;

    public DefaultScriptHandler(ScriptSource scriptSource, DependencyResolutionServices dependencyResolutionServices, ClassLoaderScope classLoaderScope,
                                ScriptClassPathResolver scriptClassPathResolver, NamedObjectInstantiator instantiator) {
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.scriptResource = scriptSource.getResource().getLocation();
        this.classLoaderScope = classLoaderScope;
        this.scriptClassPathResolver = scriptClassPathResolver;
        this.instantiator = instantiator;
        this.dependencyLockingHandler = dependencyResolutionServices.getDependencyLockingHandler();
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
        return scriptClassPathResolver.resolveClassPath(classpathConfiguration);
    }

    @Override
    public DependencyHandler getDependencies() {
        defineConfiguration();
        if (dependencyHandler == null) {
            dependencyHandler = dependencyResolutionServices.getDependencyHandler();
        }
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

    private void defineConfiguration() {
        // Defer creation and resolution of configuration until required. Short-circuit when script does not require classpath
        if (configContainer == null) {
            configContainer = dependencyResolutionServices.getConfigurationContainer();
        }
        if (classpathConfiguration == null) {
            classpathConfiguration = configContainer.create(CLASSPATH_CONFIGURATION);
            // should ideally reuse the `JvmEcosystemUtilities` but this code is too low level
            // and this service is therefore not available!
            AttributeContainer attributes = classpathConfiguration.getAttributes();
            attributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, instantiator.named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, instantiator.named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, instantiator.named(Bundling.class, Bundling.EXTERNAL));
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.valueOf(JavaVersion.current().getMajorVersion()));
        }
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

    @Override
    public DynamicObject getAsDynamicObject() {
        if (dynamicObject == null) {
            dynamicObject = new BeanDynamicObject(this);
        }
        return dynamicObject;
    }
}
