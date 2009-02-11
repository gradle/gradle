/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.internal.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.dependencies.DefaultExcludeRuleContainer;
import org.gradle.api.internal.dependencies.DependencyContainerInternal;
import org.gradle.api.internal.dependencies.ivyservice.ClientModuleDescriptorFactory;
import org.gradle.api.internal.dependencies.ivyservice.DefaultClientModuleDescriptorFactory;
import org.gradle.api.internal.dependencies.ivyservice.DefaultDependencyDescriptorFactory;
import org.gradle.api.internal.dependencies.ivyservice.DependencyDescriptorFactory;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class ClientModule implements ExternalDependency, IClientModule {
    public static final String CLIENT_MODULE_KEY = "org.gradle.clientModule";

    private ExcludeRuleContainer excludeRules = new DefaultExcludeRuleContainer();

    private DependencyConfigurationMappingContainer dependencyConfigurationMappings;

    private String id;

    private String group;

    private String name;

    private String version;

    private boolean force = false;

    private boolean transitive = true;

    private List<DependencyArtifact> artifacts = new ArrayList<DependencyArtifact>();

    private ChainingTransformer<DependencyDescriptor> transformer
            = new ChainingTransformer<DependencyDescriptor>(DependencyDescriptor.class);

    private DependencyContainerInternal dependencyContainer;

    private DependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory();

    private ClientModuleDescriptorFactory clientModuleDescriptorFactory = new DefaultClientModuleDescriptorFactory();

    public ClientModule() {
    }

    public ClientModule(DependencyConfigurationMappingContainer dependencyConfigurationMappings,
                        String id, DependencyContainerInternal dependencyContainer) {
        if (id == null) {
            throw new InvalidUserDataException("Client module notation must not be null.");
        }
        this.id = id;
        this.dependencyConfigurationMappings = dependencyConfigurationMappings;
        this.dependencyContainer = dependencyContainer;
        initFromUserDescription(id);
    }

    private void initFromUserDescription(String userDescription) {
        String[] moduleDescriptionParts = userDescription.split(":");
        throwExceptionIfInvalidDescription(moduleDescriptionParts, userDescription);
        setModulePropertiesFromParsedDescription(moduleDescriptionParts);
        boolean hasClassifier = moduleDescriptionParts.length == 4;
        if (hasClassifier) {
            String classifier = moduleDescriptionParts[3];
            artifacts.add(new DefaultDependencyArtifact(name, DependencyArtifact.DEFAULT_TYPE, DependencyArtifact.DEFAULT_TYPE, classifier, null));
        }
    }

    private void throwExceptionIfInvalidDescription(String[] moduleDescriptionParts, String userDescription) {
        int length = moduleDescriptionParts.length;
        if (length < 3 || length > 4) {
            throw new InvalidUserDataException("Client module notation is invalid: " + userDescription);
        }
    }

    private void setModulePropertiesFromParsedDescription(String[] dependencyParts) {
        group = dependencyParts[0];
        name = dependencyParts[1];
        version = dependencyParts[2];
    }

    public void addIvyTransformer(Transformer<DependencyDescriptor> dependencyDescriptorTransformer) {
        this.transformer.add(dependencyDescriptorTransformer);
    }

    public void addIvyTransformer(Closure transformer) {
        this.transformer.add(transformer);
    }

    public DependencyDescriptor createDependencyDescriptor(ModuleDescriptor parent) {
        DependencyDescriptor dd = dependencyDescriptorFactory.createFromClientModule(parent, this);
        ModuleDescriptor moduleDescriptor = clientModuleDescriptorFactory.createModuleDescriptor(dd.getDependencyRevisionId(), dependencyContainer);
        dependencyContainer.getClientModuleRegistry().put(id, moduleDescriptor);
        return transformer.transform(dd);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroup() {
        return group;
    }

    public ClientModule setGroup(String group) {
        this.group = group;
        return this;
    }

    public String getName() {
        return name;
    }

    public ClientModule setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public ClientModule setVersion(String version) {
        this.version = version;
        return this;
    }

    public boolean isForce() {
        return force;
    }

    public ClientModule setForce(boolean force) {
        this.force = force;
        return this;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public ClientModule setTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
    }

    public List<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<DependencyArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public ClientModule addArtifact(DependencyArtifact artifact) {
        artifacts.add(artifact);
        return this;
    }

    public DependencyArtifact artifact(Closure configureClosure) {
        DependencyArtifact artifact =  (DependencyArtifact) ConfigureUtil.configure(configureClosure, new DefaultDependencyArtifact());
        artifacts.add(artifact);
        return artifact;
    }

    public Dependency exclude(Map<String, String> excludeProperties) {
        excludeRules.add(excludeProperties);
        return this;
    }

    public Dependency exclude(Map<String, String> excludeProperties, List<String> confs) {
        excludeRules.add(excludeProperties, confs);
        return this;
    }

    public ExcludeRuleContainer getExcludeRules() {
        return excludeRules;
    }

    public void setExcludeRules(ExcludeRuleContainer excludeRules) {
        this.excludeRules = excludeRules;
    }

    public DependencyConfigurationMappingContainer getDependencyConfigurationMappings() {
        return dependencyConfigurationMappings;
    }

    public void setDependencyConfigurationMappings(DependencyConfigurationMappingContainer dependencyConfigurationMappings) {
        this.dependencyConfigurationMappings = dependencyConfigurationMappings;
    }

    public void addDependencyConfiguration(String... dependencyConfigurations) {
        dependencyConfigurationMappings.add(dependencyConfigurations);
    }

    public void addConfigurationMapping(Map<org.gradle.api.dependencies.Configuration, List<String>> dependencyConfigurations) {
        dependencyConfigurationMappings.add(dependencyConfigurations);
    }

    public Map<org.gradle.api.dependencies.Configuration, List<String>> getConfigurationMappings() {
        return dependencyConfigurationMappings.getMappings();
    }

    public void addConfiguration(org.gradle.api.dependencies.Configuration... masterConfigurations) {
        dependencyConfigurationMappings.addMasters(masterConfigurations);
    }

    public List<String> getDependencyConfigurations(String configuration) {
        return dependencyConfigurationMappings.getDependencyConfigurations(configuration);
    }

    public Set<org.gradle.api.dependencies.Configuration> getConfigurations() {
        return dependencyConfigurationMappings.getMasterConfigurations();
    }

    public void dependencies(Object... dependencies) {
        dependencyContainer.dependencies(WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION), dependencies);
    }

    public ClientModule clientModule(String artifact) {
        return clientModule(artifact, null);
    }

    public ClientModule clientModule(String artifact, Closure configureClosure) {
        return dependencyContainer.clientModule(WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION), artifact, configureClosure);
    }

    public Dependency dependency(String id) {
        return dependency(id, null);
    }

    public Dependency dependency(String id, Closure configureClosure) {
        return dependencyContainer.dependency(WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION), id, configureClosure);
    }

    public DependencyContainer getDependencyContainer() {
        return dependencyContainer;
    }

    public void setDependencyContainer(DependencyContainerInternal dependencyContainer) {
        this.dependencyContainer = dependencyContainer;
    }

    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }

    public ClientModuleDescriptorFactory getClientModuleDescriptorFactory() {
        return clientModuleDescriptorFactory;
    }

    public void setClientModuleDescriptorFactory(ClientModuleDescriptorFactory clientModuleDescriptorFactory) {
        this.clientModuleDescriptorFactory = clientModuleDescriptorFactory;
    }
}
