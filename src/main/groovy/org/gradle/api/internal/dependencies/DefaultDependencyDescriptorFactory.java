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

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.dependencies.ivy.IvyUtil;
import org.gradle.api.dependencies.*;
import org.gradle.api.dependencies.Artifact;
import org.gradle.util.WrapUtil;

import java.util.*;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyDescriptorFactory implements DependencyDescriptorFactory {

    public DependencyDescriptor createFromClientModule(ModuleDescriptor parent, ClientModule clientModule) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                ModuleRevisionId.newInstance(clientModule.getGroup(),
                        clientModule.getName(),
                        clientModule.getVersion(),
                        WrapUtil.toMap(ClientModule.CLIENT_MODULE_KEY, clientModule.getId())),
                clientModule.isForce(),
                false,
                clientModule.isTransitive());
        addArtifacts(clientModule.getArtifacts(), dependencyDescriptor);
        addExcludes(clientModule.getExcludeRules(), dependencyDescriptor, IvyUtil.getAllMasterConfs(parent.getConfigurations()));
        addDependencyConfigurations(clientModule.getDependencyConfigurationMappings(), dependencyDescriptor);
        return dependencyDescriptor;
    }

    public DependencyDescriptor createFromProjectDependency(ModuleDescriptor parent, ProjectDependency dependency) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                dependency.getDependencyProject().getDependencies().createModuleRevisionId(),
                false,
                true,
                dependency.isTransitive());
        addExcludes(dependency.getExcludeRules(), dependencyDescriptor, IvyUtil.getAllMasterConfs(parent.getConfigurations()));
        addDependencyConfigurations(dependency.getDependencyConfigurationMappings(), dependencyDescriptor); 
        return dependencyDescriptor;
    }

    public DependencyDescriptor createFromModuleDependency(ModuleDescriptor parent, ModuleDependency moduleDependency) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                ModuleRevisionId.newInstance(moduleDependency.getGroup(),
                        moduleDependency.getName(),
                        moduleDependency.getVersion()),
                moduleDependency.isForce(),
                moduleDependency.isChanging(),
                moduleDependency.isTransitive());
        addArtifacts(moduleDependency.getArtifacts(), dependencyDescriptor);
        addExcludes(moduleDependency.getExcludeRules(), dependencyDescriptor, IvyUtil.getAllMasterConfs(parent.getConfigurations()));
        addDependencyConfigurations(moduleDependency.getDependencyConfigurationMappings(), dependencyDescriptor);
        return dependencyDescriptor;
    }

    private void addArtifacts(List<Artifact> artifacts, DefaultDependencyDescriptor dependencyDescriptor) {
        for (Artifact artifact : artifacts) {
            DefaultDependencyArtifactDescriptor artifactDescriptor = null;
            try {
                artifactDescriptor = new DefaultDependencyArtifactDescriptor(
                        dependencyDescriptor,
                        artifact.getName(),
                        artifact.getType(),
                        artifact.getExtension() != null ? artifact.getExtension() : artifact.getType(),
                        artifact.getUrl() != null ? new URL(artifact.getUrl()) : null,
                        artifact.getClassifier() != null ? WrapUtil.toMap(DependencyManager.CLASSIFIER, artifact.getClassifier()) : null);
            } catch (MalformedURLException e) {
                throw new InvalidUserDataException("URL for artifact can't be parsed: " + artifact.getUrl(), e);
            }
            if (artifact.getConfs().isEmpty()) {
                dependencyDescriptor.addDependencyArtifact("*", artifactDescriptor);
            } else {
                for (String conf : artifact.getConfs()) {
                    artifactDescriptor.addConfiguration(conf);
                    dependencyDescriptor.addDependencyArtifact(conf, artifactDescriptor);
                }
            }
        }
    }

    private void addDependencyConfigurations(DependencyConfigurationMappingContainer dependencyConfigurationMappings,
                                             DefaultDependencyDescriptor dependencyDescriptor) {
        for (String masterConf : dependencyConfigurationMappings.getMappings().keySet()) {
            for (String dependencyConf : dependencyConfigurationMappings.getMappings().get(masterConf)) {
                dependencyDescriptor.addDependencyConfiguration(masterConf, dependencyConf);
            }
        }
    }

    private void addExcludes(ExcludeRuleContainer excludeRules, DefaultDependencyDescriptor dependencyDescriptor, List<String> allMasterConfs) {
        for (ExcludeRule excludeRule : excludeRules.createRules(allMasterConfs)) {
            for (String masterConf : excludeRule.getConfigurations()) {
                dependencyDescriptor.addExcludeRule(masterConf, excludeRule);
            }
        }
    }
}
