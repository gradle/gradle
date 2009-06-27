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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyDescriptorFactory implements DependencyDescriptorFactory {
    private ExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter();
    private ClientModuleDescriptorFactory clientModuleDescriptorFactory = new DefaultClientModuleDescriptorFactory();

    public void addDependencyDescriptor(String configuration, DefaultModuleDescriptor moduleDescriptor,
                                        ModuleDependency dependency,
                                        Map<String, ModuleDescriptor> clientModuleRegistry) {
        // todo Make this object oriented and enhancable
        if (dependency instanceof ClientModule) {
            moduleDescriptor.addDependency(createFromClientModule(configuration, moduleDescriptor,
                    (ClientModule) dependency, clientModuleRegistry));
        } else if (dependency instanceof ProjectDependency) {
            moduleDescriptor.addDependency(createFromProjectDependency(configuration, moduleDescriptor,
                    (ProjectDependency) dependency));
        } else if (dependency instanceof ExternalModuleDependency) {
            moduleDescriptor.addDependency(createFromModuleDependency(configuration, moduleDescriptor,
                    (ExternalModuleDependency) dependency));
        } else {
            throw new GradleException("Can't map dependency of type: " + dependency.getClass());
        }
    }

    private DependencyDescriptor createFromClientModule(String configuration, ModuleDescriptor parent,
                                                        ClientModule clientModule,
                                                        Map<String, ModuleDescriptor> clientModuleRegistry) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                ModuleRevisionId.newInstance(clientModule.getGroup(), clientModule.getName(), clientModule.getVersion(),
                        WrapUtil.toMap(ClientModule.CLIENT_MODULE_KEY, clientModule.getId())), clientModule.isForce(),
                false, clientModule.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, clientModule, dependencyDescriptor);

        ModuleDescriptor moduleDescriptor = clientModuleDescriptorFactory.createModuleDescriptor(
                dependencyDescriptor.getDependencyRevisionId(), clientModule.getDependencies(), this,
                clientModuleRegistry);
        clientModuleRegistry.put(clientModule.getId(), moduleDescriptor);

        return dependencyDescriptor;
    }

    private DependencyDescriptor createFromProjectDependency(String configuration, ModuleDescriptor parent,
                                                             ProjectDependency dependency) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                createModuleRevisionIdFromDependency(dependency), false, true, dependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, dependency, dependencyDescriptor);
        return dependencyDescriptor;
    }

    private DependencyDescriptor createFromModuleDependency(String configuration, ModuleDescriptor parent,
                                                            ExternalModuleDependency externalModuleDependency) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                createModuleRevisionIdFromDependency(externalModuleDependency), externalModuleDependency.isForce(),
                externalModuleDependency.isChanging(), externalModuleDependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, externalModuleDependency, dependencyDescriptor);
        return dependencyDescriptor;
    }

    private void addExcludesArtifactsAndDependencies(String configuration, ModuleDependency dependency,
                                                     DefaultDependencyDescriptor dependencyDescriptor) {
        addArtifacts(configuration, dependency.getArtifacts(), dependencyDescriptor);
        addExcludes(configuration, dependency.getExcludeRules(), dependencyDescriptor);
        addDependencyConfiguration(configuration, dependency, dependencyDescriptor);
    }

    private ModuleRevisionId createModuleRevisionIdFromDependency(Dependency dependency) {
        return ModuleRevisionId.newInstance(emptyStringIfNull(dependency.getGroup()), dependency.getName(),
                emptyStringIfNull(dependency.getVersion()));
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
}
