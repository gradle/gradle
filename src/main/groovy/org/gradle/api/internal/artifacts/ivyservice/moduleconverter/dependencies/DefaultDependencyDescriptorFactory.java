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

import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.util.WrapUtil;

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

    public DependencyDescriptor createDependencyDescriptor(String configuration, ModuleDescriptor parent,
                                                           Dependency dependency, Map clientModuleRegistry) {
        // todo Make this object oriented and enhancable
        if (dependency instanceof ClientModule) {
            return createFromClientModule(configuration, parent, (ClientModule) dependency, clientModuleRegistry);
        } else if (dependency instanceof ProjectDependency) {
            return createFromProjectDependency(configuration, parent, (ProjectDependency) dependency);
        } else if (dependency instanceof ModuleDependency) {
            return createFromModuleDependency(configuration, parent, (ModuleDependency) dependency);
        }
        throw new GradleException("Can't map dependency of type: " + dependency.getClass());
    }

    private DependencyDescriptor createFromClientModule(String configuration, ModuleDescriptor parent,
                                                        ClientModule clientModule, Map clientModuleRegistry) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                ModuleRevisionId.newInstance(clientModule.getGroup(),
                        clientModule.getName(),
                        clientModule.getVersion(),
                        WrapUtil.toMap(ClientModule.CLIENT_MODULE_KEY, clientModule.getId())),
                clientModule.isForce(),
                false,
                clientModule.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, clientModule, dependencyDescriptor);

        ModuleDescriptor moduleDescriptor = clientModuleDescriptorFactory.createModuleDescriptor(
                dependencyDescriptor.getDependencyRevisionId(), clientModule.getDependencies(), this, clientModuleRegistry);
        clientModuleRegistry.put(clientModule.getId(), moduleDescriptor);
        
        return dependencyDescriptor;
    }

    private DependencyDescriptor createFromProjectDependency(String configuration, ModuleDescriptor parent, ProjectDependency dependency) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                createModuleRevisionIdFromDependency(dependency),
                false,
                true,
                dependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, dependency, dependencyDescriptor);
        return dependencyDescriptor;
    }

    private DependencyDescriptor createFromModuleDependency(String configuration, ModuleDescriptor parent, ModuleDependency moduleDependency) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                createModuleRevisionIdFromDependency(moduleDependency),
                moduleDependency.isForce(),
                moduleDependency.isChanging(),
                moduleDependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, moduleDependency, dependencyDescriptor);
        return dependencyDescriptor;
    }

    private void addExcludesArtifactsAndDependencies(String configuration, Dependency dependency, DefaultDependencyDescriptor dependencyDescriptor) {
        addArtifacts(configuration, dependency.getArtifacts(), dependencyDescriptor);
        addExcludes(configuration, dependency.getExcludeRules(), dependencyDescriptor);
        addDependencyConfiguration(configuration, dependency, dependencyDescriptor);
    }

    private ModuleRevisionId createModuleRevisionIdFromDependency(Dependency dependency) {
        return ModuleRevisionId.newInstance(dependency.getGroup(),
                dependency.getName(),
                dependency.getVersion());
    }

    private void addArtifacts(String configuration, Set<DependencyArtifact> artifacts, DefaultDependencyDescriptor dependencyDescriptor) {
        for (DependencyArtifact artifact : artifacts) {
            DefaultDependencyArtifactDescriptor artifactDescriptor = null;
            try {
                artifactDescriptor = new DefaultDependencyArtifactDescriptor(
                        dependencyDescriptor,
                        artifact.getName(),
                        artifact.getType(),
                        artifact.getExtension() != null ? artifact.getExtension() : artifact.getType(),
                        artifact.getUrl() != null ? new URL(artifact.getUrl()) : null,
                        artifact.getClassifier() != null ? WrapUtil.toMap(Dependency.CLASSIFIER, artifact.getClassifier()) : null);
            } catch (MalformedURLException e) {
                throw new InvalidUserDataException("URL for artifact can't be parsed: " + artifact.getUrl(), e);
            }
            dependencyDescriptor.addDependencyArtifact(configuration, artifactDescriptor);
        }
    }

    private void addDependencyConfiguration(String configuration, Dependency dependency, DefaultDependencyDescriptor dependencyDescriptor) {
        dependencyDescriptor.addDependencyConfiguration(configuration, dependency.getDependencyConfiguration());
    }

    private void addExcludes(String configuration, Set<ExcludeRule> excludeRules, DefaultDependencyDescriptor dependencyDescriptor) {
        for (ExcludeRule excludeRule : excludeRules) {
            dependencyDescriptor.addExcludeRule(configuration, excludeRuleConverter.createExcludeRule(excludeRule));
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
