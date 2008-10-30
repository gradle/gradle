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
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.dependencies.*;
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.WrapUtil;
import org.gradle.util.ConfigureUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class ClientModule extends DefaultDependencyContainer implements ExternalDependency, Dependency {
    public static final String CLIENT_MODULE_KEY = "org.gradle.clientModule";

    private ExcludeRuleContainer excludeRules = new DefaultExcludeRuleContainer();

    private DependencyConfigurationMappingContainer dependencyConfigurationMappings;

    private String id;

    private String group;

    private String name;

    private String version;

    private boolean force = false;

    private boolean transitive = true;

    private List<Artifact> artifacts = new ArrayList<Artifact>();

    private DependencyDescriptorFactory dependencyDescriptorFactory = new DefaultDependencyDescriptorFactory();

    public ClientModule() {
    }

    public ClientModule(DependencyFactory dependencyFactory, DependencyConfigurationMappingContainer dependencyConfigurationMappings,
                        String id, Map moduleRegistry) {
        super(dependencyFactory, WrapUtil.toList(Dependency.DEFAULT_CONFIGURATION));
        if (id == null) {
            throw new InvalidUserDataException("Client module notation must not be null.");
        }
        this.id = id;
        setClientModuleRegistry(moduleRegistry);
        this.dependencyConfigurationMappings = dependencyConfigurationMappings;
        initFromUserDescription(id);
    }

    private void initFromUserDescription(String userDescription) {
        String[] moduleDescriptionParts = userDescription.split(":");
        throwExceptionIfInvalidDescription(moduleDescriptionParts, userDescription);
        setModulePropertiesFromParsedDescription(moduleDescriptionParts);
        boolean hasClassifier = moduleDescriptionParts.length == 4;
        if (hasClassifier) {
            String classifier = moduleDescriptionParts[3];
            artifacts.add(new Artifact(name, Artifact.DEFAULT_TYPE, Artifact.DEFAULT_TYPE, classifier, null));
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

    public DependencyDescriptor createDependencyDescriptor(ModuleDescriptor parent) {
//        DependencyDescriptor dd = dependencyDescriptorFactory.createDescriptor(parent, id, false, true, true, confs, DefaultExcludeRuleContainer.NO_RULES,
//                WrapUtil.toMap(CLIENT_MODULE_KEY, id));
        DependencyDescriptor dd = dependencyDescriptorFactory.createFromClientModule(parent, this);
        addModuleDescriptors(dd.getDependencyRevisionId());
        return dd;
    }

    private void addModuleDescriptors(ModuleRevisionId moduleRevisionId) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(moduleRevisionId,
                "release", null);
        moduleDescriptor.addConfiguration(new Configuration(Dependency.DEFAULT_CONFIGURATION));
        addDependencyDescriptors(moduleDescriptor);
        moduleDescriptor.addArtifact(Dependency.DEFAULT_CONFIGURATION, new DefaultArtifact(moduleRevisionId, null, moduleRevisionId.getName(), "jar", "jar"));
        this.getClientModuleRegistry().put(id, moduleDescriptor);
    }

    public void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor) {
        List<DependencyDescriptor> dependencyDescriptors = new ArrayList<DependencyDescriptor>();
        for (Dependency dependency : getDependencies()) {
            dependencyDescriptors.add(dependency.createDependencyDescriptor(moduleDescriptor));
        }
        dependencyDescriptors.addAll(getDependencyDescriptors());
        for (DependencyDescriptor dependencyDescriptor : dependencyDescriptors) {
            moduleDescriptor.addDependency(dependencyDescriptor);
        }
    }

    public void dependencies(List<String> confs, Object... dependencies) {
        if (!confs.equals(getDefaultConfs())) {
            throw new UnsupportedOperationException("You can assign confs " + confs + " to dependencies in a client module.");
        }
        super.dependencies(confs, dependencies);
    }

    public Dependency dependency(List confs, Object id) {
        return dependency(confs, id, null);
    }

    public Dependency dependency(List confs, Object id, Closure configureClosure) {
        if (!confs.equals(getDefaultConfs())) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.");
        }
        return super.dependency(confs, id, configureClosure);
    }

    public ClientModule clientModule(List confs, String artifact) {
        return clientModule(confs, artifact, null);
    }

    public ClientModule clientModule(List confs, String artifact, Closure configureClosure) {
        if (!confs.equals(getDefaultConfs())) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.");
        }
        return super.clientModule(confs, artifact, configureClosure);
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

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    public ClientModule addArtifact(Artifact artifact) {
        artifacts.add(artifact);
        return this;
    }

    public Artifact artifact(Closure configureClosure) {
        Artifact artifact =  (Artifact) ConfigureUtil.configure(configureClosure, new Artifact());
        artifacts.add(artifact);
        return artifact;
    }

    public DependencyDescriptorFactory getDependencyDescriptorFactory() {
        return dependencyDescriptorFactory;
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
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

    public void dependencyConfigurations(String... dependencyConfigurations) {
        dependencyConfigurationMappings.add(dependencyConfigurations);
    }

    public void dependencyConfigurations(Map<String, List<String>> dependencyConfigurations) {
        dependencyConfigurationMappings.add(dependencyConfigurations);
    }
}
