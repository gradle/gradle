/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies;

import org.gradle.api.dependencies.*;
import org.gradle.api.filter.FilterSpec;
import org.gradle.api.filter.Filters;
import org.gradle.api.Project;
import org.gradle.util.GUtil;
import org.gradle.util.ConfigureUtil;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

import java.util.*;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyContainer implements DependencyContainerInternal {
    private Project project;
    private ConfigurationContainer configurationContainer;
    private DependencyFactory dependencyFactory;
    private List<Dependency> dependencies = new ArrayList<Dependency>();
    private ExcludeRuleContainer excludeRules;
    private Map<String, ModuleDescriptor> clientModuleRegistry;
    

    public DefaultDependencyContainer(Project project, ConfigurationContainer configurationContainer, DependencyFactory dependencyFactory,
                                      ExcludeRuleContainer excludeRuleContainer, Map<String, ModuleDescriptor> clientModuleRegistry) {
        this.project = project;
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.excludeRules = excludeRuleContainer;
        this.clientModuleRegistry = clientModuleRegistry;
    }

    public void dependencies(List<String> confs, Object... dependencies) {
        dependencies(getStandardConfigurationMapping(confs).getMappings(), dependencies);
    }

    public void addDependencies(Dependency... dependencies) {
        this.dependencies.addAll(Arrays.asList(dependencies));
    }

    public Dependency dependency(List<String> confs, Object id, Closure configureClosure) {
        return dependency(getStandardConfigurationMapping(confs).getMappings(), id, configureClosure);
    }

    public void dependencies(Map<Configuration, List<String>> configurationMappings, Object... dependencies) {
        for (Object dependency : GUtil.flatten(Arrays.asList(dependencies))) {
            this.dependencies.add(dependencyFactory.createDependency(
                    getStandardConfigurationMapping(configurationMappings), dependency, project));
        }
    }

    public Dependency dependency(Map<Configuration, List<String>> configurationMappings, Object userDependencyDescription, Closure configureClosure) {
        Dependency dependency = dependencyFactory.createDependency(getStandardConfigurationMapping(configurationMappings), userDependencyDescription, project);
        dependencies.add(dependency);
        ConfigureUtil.configure(configureClosure, dependency);
        return dependency;
    }


    public ClientModule clientModule(List<String> confs, String id) {
        return clientModule(confs, id, null);
    }

    public ClientModule clientModule(List<String> confs, String id, Closure configureClosure) {
        return clientModule(getStandardConfigurationMapping(confs).getMappings(), id, configureClosure);
    }

    public ClientModule clientModule(Map<Configuration, List<String>> configurationMappings, String moduleDescriptor) {
        return clientModule(configurationMappings, moduleDescriptor, null);
    }

    public ClientModule clientModule(Map<Configuration, List<String>> configurationMappings, String moduleDescriptor, Closure configureClosure) {
        // todo: We might better have a client module factory here
        DefaultConfigurationContainer defaultConfigurationContainer = new DefaultConfigurationContainer();
        defaultConfigurationContainer.add(Dependency.DEFAULT_CONFIGURATION);
        ClientModule clientModule = new ClientModule(getStandardConfigurationMapping(configurationMappings), moduleDescriptor,
                new DefaultDependencyContainer(project, defaultConfigurationContainer,
                        dependencyFactory, new DefaultExcludeRuleContainer(), clientModuleRegistry));
        dependencies.add(clientModule);
        ConfigureUtil.configure(configureClosure, clientModule);
        return clientModule;
    }

    public <T extends Dependency> List<T> getDependencies(FilterSpec<T> filter) {
        return Filters.filterIterable((Iterable<T>) getDependencies(), filter);
    }

    private Configuration[] getConfigurations(List<String> masterConfs) {
        List<Configuration> result = new ArrayList<Configuration>();
        for (String masterConf : masterConfs) {
            result.add(configurationContainer.get(masterConf));
        }
        return result.toArray(new Configuration[result.size()]);
    }

    private DependencyConfigurationMappingContainer getStandardConfigurationMapping(final Map<Configuration, List<String>> confMapping) {
        return new DefaultDependencyConfigurationMappingContainer(confMapping);
    }

    private DependencyConfigurationMappingContainer getStandardConfigurationMapping(final List<String> masterConfs) {
        DependencyConfigurationMappingContainer mappingContainer =  new DefaultDependencyConfigurationMappingContainer();
        mappingContainer.addMasters(getConfigurations(masterConfs));
        return mappingContainer;
    }

    public ConfigurationContainer getConfigurationContainer() {
        return configurationContainer;
    }

    public List<? extends Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }
    
    public DependencyFactory getDependencyFactory() {
        return dependencyFactory;
    }

    public void setDependencyFactory(DependencyFactory dependencyFactory) {
        this.dependencyFactory = dependencyFactory;
    }

    public ExcludeRuleContainer getExcludeRules() {
        return excludeRules;
    }

    public void setExcludeRules(ExcludeRuleContainer excludeRules) {
        this.excludeRules = excludeRules;
    }

    public Project getProject() {
        return project;
    }

    public Map<String, ModuleDescriptor> getClientModuleRegistry() {
        return clientModuleRegistry;
    }

    public Set<Configuration> getConfigurations() {
        return configurationContainer.get();
    }
}
