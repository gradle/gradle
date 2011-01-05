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
package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.AutoCreateDomainObjectContainer;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.IvyService;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainer extends AutoCreateDomainObjectContainer<Configuration> 
        implements ConfigurationContainer, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    
    private final IvyService ivyService;
    private final ClassGenerator classGenerator;
    private final DomainObjectContext context;

    private int detachedConfigurationDefaultNameCounter = 1;

    public DefaultConfigurationContainer(IvyService ivyService, ClassGenerator classGenerator, DomainObjectContext context) {
        super(Configuration.class, classGenerator);
        this.ivyService = ivyService;
        this.classGenerator = classGenerator;
        this.context = context;
    }

    @Override
    protected Configuration create(String name) {
        return classGenerator.newInstance(DefaultConfiguration.class, context.absoluteProjectPath(name), name, this, ivyService);
    }

    @Override
    public String getTypeDisplayName() {
        return "configuration";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name));
    }

    public IvyService getIvyService() {
        return ivyService;
    }

    public Configuration detachedConfiguration(Dependency... dependencies) {
        DetachedConfigurationsProvider detachedConfigurationsProvider = new DetachedConfigurationsProvider();
        String name = DETACHED_CONFIGURATION_DEFAULT_NAME + detachedConfigurationDefaultNameCounter++;
        DefaultConfiguration detachedConfiguration = new DefaultConfiguration(name, name,
                detachedConfigurationsProvider, ivyService);
        for (Dependency dependency : dependencies) {
            detachedConfiguration.addDependency(dependency.copy());
        }
        detachedConfigurationsProvider.setTheOnlyConfiguration(detachedConfiguration);
        return detachedConfiguration;
    }
}
