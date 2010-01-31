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
package org.gradle.api.internal.artifacts.publish.maven.dependencies;

import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.GradleException;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultPomDependenciesConverter implements PomDependenciesConverter {
    private ExcludeRuleConverter excludeRuleConverter;

    public DefaultPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public List<MavenDependency> convert(MavenPom pom, Set<Configuration> configurations) {
        Map<ModuleDependency, String> dependenciesMap = createDependencyToScopeMap(pom, configurations);
        List<MavenDependency> mavenDependencies = new ArrayList<MavenDependency>();
        for (ModuleDependency dependency : dependenciesMap.keySet()) {
            if (dependency.getArtifacts().size() == 0) {
                addFromDependencyDescriptor(mavenDependencies, dependency, dependenciesMap.get(dependency));
            } else {
                addFromArtifactDescriptor(mavenDependencies, dependency, dependenciesMap.get(dependency));
            }
        }
        return mavenDependencies;
    }

    private Map<ModuleDependency, String> createDependencyToScopeMap(MavenPom pom, Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations = createDependencyToConfigurationsMap(configurations);
        Map<ModuleDependency, String> dependencyToScope = new HashMap<ModuleDependency, String>();
        for (ModuleDependency dependency : dependencyToConfigurations.keySet()) {
            Conf2ScopeMapping conf2ScopeMapping = pom.getScopeMappings().getMapping(dependencyToConfigurations.get(dependency));
            if (!useScope(pom, conf2ScopeMapping)) {
                continue;
            }
            dependencyToScope.put(findDependency(dependency, conf2ScopeMapping.getConfiguration()),
                    conf2ScopeMapping.getScope());
        }
        return dependencyToScope;
    }

    private ModuleDependency findDependency(ModuleDependency dependency, Configuration configuration) {
        for (ModuleDependency configurationDependency : configuration.getDependencies(ModuleDependency.class)) {
            if (dependency.equals(configurationDependency)) {
                return configurationDependency;
            }
        }
        throw new GradleException("Dependency could not be found. We should never get here!");
    }

    private boolean useScope(MavenPom pom, Conf2ScopeMapping conf2ScopeMapping) {
        return conf2ScopeMapping.getScope() != null || !pom.getScopeMappings().isSkipUnmappedConfs();
    }

    private Map<ModuleDependency, Set<Configuration>> createDependencyToConfigurationsMap(Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencySetMap = new HashMap<ModuleDependency, Set<Configuration>>();
        for (Configuration configuration : configurations) {
            for (ModuleDependency dependency : configuration.getDependencies(ModuleDependency.class)) {
                if (dependencySetMap.get(dependency) == null) {
                    dependencySetMap.put(dependency, new HashSet<Configuration>());
                }
                dependencySetMap.get(dependency).add(configuration);
            }
        }
        return dependencySetMap;
    }

    private void addFromArtifactDescriptor(List<MavenDependency> mavenDependencies, ModuleDependency dependency, String scope) {
        for (DependencyArtifact artifact : dependency.getArtifacts()) {
            mavenDependencies.add(createMavenDependencyFromArtifactDescriptor(dependency, artifact, scope));
        }
    }

    private void addFromDependencyDescriptor(List<MavenDependency> mavenDependencies, ModuleDependency dependency, String scope) {
        mavenDependencies.add(createMavenDependencyFromDependencyDescriptor(dependency, scope));
    }

    private MavenDependency createMavenDependencyFromArtifactDescriptor(ModuleDependency dependency, DependencyArtifact artifact, String scope) {
        return createMavenDependency(dependency, artifact.getName(), artifact.getType(), scope, artifact.getClassifier());
    }

    private MavenDependency createMavenDependencyFromDependencyDescriptor(ModuleDependency dependency, String scope) {
        return createMavenDependency(dependency, dependency.getName(), null, scope, null);
    }

    private MavenDependency createMavenDependency(ModuleDependency dependency, String name, String type, String scope, String classifier) {
        return new DefaultMavenDependency(
                dependency.getGroup(), name, dependency.getVersion(), type, scope, getExcludes(dependency), false, classifier);
    }

    private List<MavenExclude> getExcludes(ModuleDependency dependency) {
        List<MavenExclude> mavenExcludes = new ArrayList<MavenExclude>();
        for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
            DefaultMavenExclude mavenExclude = excludeRuleConverter.convert(excludeRule);
            if (mavenExclude != null) {
                mavenExcludes.add(mavenExclude);
            }
        }
        return mavenExcludes;
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }
}
