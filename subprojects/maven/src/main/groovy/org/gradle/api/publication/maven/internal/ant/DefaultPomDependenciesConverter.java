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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.publication.maven.internal.ExcludeRuleConverter;
import org.gradle.api.publication.maven.internal.PomDependenciesConverter;

import java.util.*;

public class DefaultPomDependenciesConverter implements PomDependenciesConverter {
    private ExcludeRuleConverter excludeRuleConverter;

    public DefaultPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public List<org.apache.maven.model.Dependency> convert(Conf2ScopeMappingContainer conf2ScopeMappingContainer, Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations = createDependencyToConfigurationsMap(configurations);
        Map<ModuleDependency, String> dependenciesMap = createDependencyToScopeMap(conf2ScopeMappingContainer, dependencyToConfigurations);
        List<org.apache.maven.model.Dependency> mavenDependencies = new ArrayList<org.apache.maven.model.Dependency>();
        for (ModuleDependency dependency : dependenciesMap.keySet()) {
            String scope = dependenciesMap.get(dependency);
            Set<Configuration> dependencyConfigurations = dependencyToConfigurations.get(dependency);
            if (dependency.getArtifacts().size() == 0) {
                addFromDependencyDescriptor(mavenDependencies, dependency, scope, dependencyConfigurations);
            } else {
                addFromArtifactDescriptor(mavenDependencies, dependency, scope, dependencyConfigurations);
            }
        }
        return mavenDependencies;
    }
    
    private Map<ModuleDependency, String> createDependencyToScopeMap(Conf2ScopeMappingContainer conf2ScopeMappingContainer, 
            Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations) {
        Map<ModuleDependency, String> dependencyToScope = new HashMap<ModuleDependency, String>();
        for (ModuleDependency dependency : dependencyToConfigurations.keySet()) {
            Conf2ScopeMapping conf2ScopeDependencyMapping = conf2ScopeMappingContainer.getMapping(dependencyToConfigurations.get(dependency));
            if (!useScope(conf2ScopeMappingContainer, conf2ScopeDependencyMapping)) {
                continue;
            }
            dependencyToScope.put(findDependency(dependency, conf2ScopeDependencyMapping.getConfiguration()),
                    conf2ScopeDependencyMapping.getScope());
        }
        return dependencyToScope;
    }

    private ModuleDependency findDependency(ModuleDependency dependency, Configuration configuration) {
        for (ModuleDependency configurationDependency : configuration.getDependencies().withType(ModuleDependency.class)) {
            if (dependency.equals(configurationDependency)) {
                return configurationDependency;
            }
        }
        throw new GradleException("Dependency could not be found. We should never get here!");
    }

    private boolean useScope(Conf2ScopeMappingContainer conf2ScopeMappingContainer, Conf2ScopeMapping conf2ScopeMapping) {
        return conf2ScopeMapping.getScope() != null || !conf2ScopeMappingContainer.isSkipUnmappedConfs();
    }

    private Map<ModuleDependency, Set<Configuration>> createDependencyToConfigurationsMap(Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencySetMap = new HashMap<ModuleDependency, Set<Configuration>>();
        for (Configuration configuration : configurations) {
            for (ModuleDependency dependency : configuration.getDependencies().withType(ModuleDependency.class)) {
                if (dependencySetMap.get(dependency) == null) {
                    dependencySetMap.put(dependency, new HashSet<Configuration>());
                }
                dependencySetMap.get(dependency).add(configuration);
            }
        }
        return dependencySetMap;
    }

    private void addFromArtifactDescriptor(List<Dependency> mavenDependencies, ModuleDependency dependency, String scope, 
            Set<Configuration> configurations) {
        for (DependencyArtifact artifact : dependency.getArtifacts()) {
            mavenDependencies.add(createMavenDependencyFromArtifactDescriptor(dependency, artifact, scope, configurations));
        }
    }

    private void addFromDependencyDescriptor(List<Dependency> mavenDependencies, ModuleDependency dependency, String scope, 
            Set<Configuration> configurations) {
        mavenDependencies.add(createMavenDependencyFromDependencyDescriptor(dependency, scope, configurations));
    }

    private Dependency createMavenDependencyFromArtifactDescriptor(ModuleDependency dependency, DependencyArtifact artifact, String scope,
            Set<Configuration> configurations) {
        return createMavenDependency(dependency, artifact.getName(), artifact.getType(), scope, artifact.getClassifier(), configurations);
    }

    private Dependency createMavenDependencyFromDependencyDescriptor(ModuleDependency dependency, String scope, Set<Configuration> configurations) {
        return createMavenDependency(dependency, dependency.getName(), null, scope, null, configurations);
    }

    private Dependency createMavenDependency(ModuleDependency dependency, String name, String type, String scope, String classifier,
            Set<Configuration> configurations) {
        Dependency mavenDependency =  new Dependency();
        mavenDependency.setGroupId(dependency.getGroup());
        if (dependency instanceof ProjectDependency) {
            mavenDependency.setArtifactId(determineProjectDependencyArtifactId((ProjectDependency) dependency));
        } else {
            mavenDependency.setArtifactId(name);
        }
        mavenDependency.setVersion(dependency.getVersion());
        mavenDependency.setType(type);
        mavenDependency.setScope(scope);
        mavenDependency.setOptional(false);
        mavenDependency.setClassifier(classifier);
        mavenDependency.setExclusions(getExclusions(dependency, configurations));
        return mavenDependency;
    }

    protected String determineProjectDependencyArtifactId(ProjectDependency dependency) {
        return new ProjectDependencyArtifactIdExtractorHack(dependency).extract();
    }

    private List<Exclusion> getExclusions(ModuleDependency dependency, Set<Configuration> configurations) {
        List<Exclusion> mavenExclusions = new ArrayList<Exclusion>();
        Set<ExcludeRule> excludeRules = new HashSet<ExcludeRule>(dependency.getExcludeRules());
        for (Configuration configuration : configurations) {
            excludeRules.addAll(configuration.getExcludeRules());
        }
        for (ExcludeRule excludeRule : excludeRules) {
            Exclusion mavenExclusion = (Exclusion) excludeRuleConverter.convert(excludeRule);
            if (mavenExclusion != null) {
                mavenExclusions.add(mavenExclusion);
            }
        }
        return mavenExclusions;
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }
}
