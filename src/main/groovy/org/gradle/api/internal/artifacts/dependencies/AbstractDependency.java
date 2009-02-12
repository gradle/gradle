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

package org.gradle.api.internal.artifacts.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.util.ConfigureUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author Hans Dockter
*/
public abstract class AbstractDependency implements Dependency {

    private ChainingTransformer<DependencyDescriptor> transformer
            = new ChainingTransformer<DependencyDescriptor>(DependencyDescriptor.class);

    private ExcludeRuleContainer excludeRules = new DefaultExcludeRuleContainer();

    private DependencyConfigurationMappingContainer dependencyConfigurationMappings;
    private List<DependencyArtifact> artifacts = new ArrayList<DependencyArtifact>();

    protected AbstractDependency() {
    }

    protected AbstractDependency(DependencyConfigurationMappingContainer dependencyConfigurationMappings) {
        this.dependencyConfigurationMappings = dependencyConfigurationMappings;
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

    public List<DependencyArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<DependencyArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public AbstractDependency addArtifact(DependencyArtifact artifact) {
        artifacts.add(artifact);
        return this;
    }

    public DependencyArtifact artifact(Closure configureClosure) {
        DependencyArtifact artifact =  (DependencyArtifact) ConfigureUtil.configure(configureClosure, new DefaultDependencyArtifact());
        artifacts.add(artifact);
        return artifact;
    }

    public void addIvyTransformer(Transformer<DependencyDescriptor> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure transformer) {
        this.transformer.add(transformer);
    }

    public Transformer<DependencyDescriptor> getTransformer() {
        return transformer;
    }

    public void addDependencyConfiguration(String... dependencyConfigurations) {
        dependencyConfigurationMappings.add(dependencyConfigurations);
    }

    public void addConfigurationMapping(Map<Configuration, List<String>> dependencyConfigurations) {
        dependencyConfigurationMappings.add(dependencyConfigurations);
    }

    public Map<Configuration, List<String>> getConfigurationMappings() {
        return dependencyConfigurationMappings.getMappings();
    }

    public List<String> getDependencyConfigurations(String configuration) {
        return dependencyConfigurationMappings.getDependencyConfigurations(configuration);
    }

    public void addConfiguration(Configuration... masterConfigurations) {
        dependencyConfigurationMappings.addMasters(masterConfigurations);
    }

    public Set<Configuration> getConfigurations() {
        return dependencyConfigurationMappings.getMasterConfigurations();
    }

    public void setDependencyConfigurations(String... confs) {
        for (Configuration configuration : getConfigurations()) {
            dependencyConfigurationMappings.setDependencyConfigurations(confs);
        }
    }


}
