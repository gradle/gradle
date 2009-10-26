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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyDescriptorFactory implements DependencyDescriptorFactory {
    private ExcludeRuleConverter excludeRuleConverter;
    private ClientModuleDescriptorFactory clientModuleDescriptorFactory;
    Map<String, ModuleDescriptor> clientModuleRegistry;

    public DefaultDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter,
                                              ClientModuleDescriptorFactory clientModuleDescriptorFactory,
                                              Map<String, ModuleDescriptor> clientModuleRegistry) {
        this.excludeRuleConverter = excludeRuleConverter;
        this.clientModuleDescriptorFactory = clientModuleDescriptorFactory;
        this.clientModuleRegistry = clientModuleRegistry;
    }

    public void addDependencyDescriptor(String configuration, DefaultModuleDescriptor moduleDescriptor,
                                        ModuleDependency dependency) {
        // todo Make this object oriented and enhancable
        InternalDependencyFactory internalDependencyFactory = null;
        if (dependency instanceof ClientModule) {
            internalDependencyFactory = new ClientModuleDependencyFactory((ClientModule) dependency,
                    configuration, moduleDescriptor, clientModuleRegistry);
        } else if (dependency instanceof ProjectDependency) {
            internalDependencyFactory = new ProjectDependencyFactory((ProjectDependency) dependency,
                    configuration, moduleDescriptor);
        } else if (dependency instanceof ExternalModuleDependency) {
            internalDependencyFactory = new ExternalModuleDependencyFactory((ExternalModuleDependency) dependency, configuration, moduleDescriptor);
        } else {
            throw new GradleException("Can't map dependency of type: " + dependency.getClass());
        }
        addDependency(configuration, moduleDescriptor, internalDependencyFactory);

    }

    private void addDependency(String configuration, DefaultModuleDescriptor moduleDescriptor, InternalDependencyFactory internalDependencyFactory) {
        ModuleRevisionId moduleRevisionId = internalDependencyFactory.createModuleRevisionId();
        DefaultDependencyDescriptor existingDependencyDescriptor = findExistingDescriptor(moduleDescriptor, moduleRevisionId);
        if (existingDependencyDescriptor == null) {
            moduleDescriptor.addDependency(internalDependencyFactory.createDependencyDescriptor(moduleRevisionId));
        } else {
            existingDependencyDescriptor.addDependencyConfiguration(configuration, internalDependencyFactory.getDependencyConfiguration());
        }
    }

    private DefaultDependencyDescriptor findExistingDescriptor(DefaultModuleDescriptor moduleDescriptor, ModuleRevisionId moduleRevisionId) {
        for (DependencyDescriptor dependencyDescriptor : moduleDescriptor.getDependencies()) {
            if (dependencyDescriptor.getDependencyRevisionId().equals(moduleRevisionId)) {
                return (DefaultDependencyDescriptor) dependencyDescriptor;
            }
        }
        return null;
    }

    private void addExcludesArtifactsAndDependencies(String configuration, ModuleDependency dependency,
                                                     DefaultDependencyDescriptor dependencyDescriptor) {
        addArtifacts(configuration, dependency.getArtifacts(), dependencyDescriptor);
        addExcludes(configuration, dependency.getExcludeRules(), dependencyDescriptor);
        addDependencyConfiguration(configuration, dependency, dependencyDescriptor);
    }

    private ModuleRevisionId createModuleRevisionIdFromDependency(Dependency dependency) {
        return createModuleRevisionIdFromDependency(dependency, Collections.<String, String>emptyMap());
    }

    private ModuleRevisionId createModuleRevisionIdFromDependency(Dependency dependency, Map<String, String> extraAttributes) {
        return ModuleRevisionId.newInstance(emptyStringIfNull(dependency.getGroup()), dependency.getName(),
                emptyStringIfNull(dependency.getVersion()), extraAttributes);
    }

    private String emptyStringIfNull(String value) {
        return GUtil.elvis(value, "");
    }

    private void addArtifacts(String configuration, Set<DependencyArtifact> artifacts,
                              DefaultDependencyDescriptor dependencyDescriptor) {
        for (DependencyArtifact artifact : artifacts) {
            DefaultDependencyArtifactDescriptor artifactDescriptor;
            try {
                artifactDescriptor = new DefaultDependencyArtifactDescriptor(dependencyDescriptor, artifact.getName(),
                        artifact.getType(),
                        artifact.getExtension() != null ? artifact.getExtension() : artifact.getType(),
                        artifact.getUrl() != null ? new URL(artifact.getUrl()) : null,
                        artifact.getClassifier() != null ? WrapUtil.toMap(Dependency.CLASSIFIER,
                                artifact.getClassifier()) : null);
            } catch (MalformedURLException e) {
                throw new InvalidUserDataException("URL for artifact can't be parsed: " + artifact.getUrl(), e);
            }
            dependencyDescriptor.addDependencyArtifact(configuration, artifactDescriptor);
        }
    }

    private void addDependencyConfiguration(String configuration, ModuleDependency dependency,
                                            DefaultDependencyDescriptor dependencyDescriptor) {
        dependencyDescriptor.addDependencyConfiguration(configuration, dependency.getConfiguration());
    }

    private void addExcludes(String configuration, Set<ExcludeRule> excludeRules,
                             DefaultDependencyDescriptor dependencyDescriptor) {
        for (ExcludeRule excludeRule : excludeRules) {
            dependencyDescriptor.addExcludeRule(configuration, excludeRuleConverter.createExcludeRule(configuration,
                    excludeRule));
        }
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }

    public void setExcludeRuleConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public ClientModuleDescriptorFactory getClientModuleDescriptorFactory() {
        return clientModuleDescriptorFactory;
    }

    public void setClientModuleDescriptorFactory(ClientModuleDescriptorFactory clientModuleDescriptorFactory) {
        this.clientModuleDescriptorFactory = clientModuleDescriptorFactory;
    }

    private interface InternalDependencyFactory {
        ModuleRevisionId createModuleRevisionId();
        DependencyDescriptor createDependencyDescriptor(ModuleRevisionId moduleRevisionId);
        String getDependencyConfiguration();
    }

    private class ExternalModuleDependencyFactory implements InternalDependencyFactory {
        private ExternalModuleDependency externalModuleDependency;
        private String configuration;
        private ModuleDescriptor parent;

        private ExternalModuleDependencyFactory(ExternalModuleDependency externalModuleDependency, String configuration, ModuleDescriptor parent) {
            this.externalModuleDependency = externalModuleDependency;
            this.configuration = configuration;
            this.parent = parent;
        }

        public ModuleRevisionId createModuleRevisionId() {
            return createModuleRevisionIdFromDependency(externalModuleDependency);
        }

        public DependencyDescriptor createDependencyDescriptor(ModuleRevisionId moduleRevisionId) {
            DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                    moduleRevisionId, externalModuleDependency.isForce(),
                    externalModuleDependency.isChanging(), externalModuleDependency.isTransitive());
            addExcludesArtifactsAndDependencies(configuration, externalModuleDependency, dependencyDescriptor);
            return dependencyDescriptor;
        }

        public String getDependencyConfiguration() {
            return externalModuleDependency.getConfiguration();
        }
    }

    private class ClientModuleDependencyFactory implements InternalDependencyFactory {
        private ClientModule clientModule;
        private String configuration;
        private ModuleDescriptor parent;
        private Map<String, ModuleDescriptor> clientModuleRegistry;

        private ClientModuleDependencyFactory(ClientModule clientModule, String configuration, ModuleDescriptor parent, Map<String, ModuleDescriptor> clientModuleRegistry) {
            this.clientModule = clientModule;
            this.configuration = configuration;
            this.parent = parent;
            this.clientModuleRegistry = clientModuleRegistry;
        }

        public ModuleRevisionId createModuleRevisionId() {
            return ModuleRevisionId.newInstance(clientModule.getGroup(), clientModule.getName(), clientModule.getVersion(),
                    WrapUtil.toMap(ClientModule.CLIENT_MODULE_KEY, clientModule.getId()));
        }

        public DependencyDescriptor createDependencyDescriptor(ModuleRevisionId moduleRevisionId) {
            DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                    moduleRevisionId, clientModule.isForce(),
                    false, clientModule.isTransitive());
            addExcludesArtifactsAndDependencies(configuration, clientModule, dependencyDescriptor);

            ModuleDescriptor moduleDescriptor = clientModuleDescriptorFactory.createModuleDescriptor(
                    dependencyDescriptor.getDependencyRevisionId(), clientModule.getDependencies(), DefaultDependencyDescriptorFactory.this);
            clientModuleRegistry.put(clientModule.getId(), moduleDescriptor);

            return dependencyDescriptor;
        }

        public String getDependencyConfiguration() {
            return clientModule.getConfiguration();
        }
    }

    private class ProjectDependencyFactory implements InternalDependencyFactory {
        private ProjectDependency projectDependency;
        private String configuration;
        private ModuleDescriptor parent;

        private ProjectDependencyFactory(ProjectDependency projectDependency, String configuration, ModuleDescriptor parent) {
            this.projectDependency = projectDependency;
            this.configuration = configuration;
            this.parent = parent;
        }

        public ModuleRevisionId createModuleRevisionId() {
            return createModuleRevisionIdFromDependency(projectDependency, WrapUtil.toMap(PROJECT_PATH_KEY,
                    projectDependency.getDependencyProject().getPath()));
        }

        public DependencyDescriptor createDependencyDescriptor(ModuleRevisionId moduleRevisionId) {
            DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                    moduleRevisionId, false, true, projectDependency.isTransitive());
            addExcludesArtifactsAndDependencies(configuration, projectDependency, dependencyDescriptor);
            return dependencyDescriptor;
        }

        public String getDependencyConfiguration() {
            return projectDependency.getConfiguration();
        }
    }

}
