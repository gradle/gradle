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
package org.gradle.api.internal.artifacts.publish.maven.dependencies;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.maven.MavenPom;

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
        Map<Dependency, Set<String>> dependenciesMap = createDependenciesMap(configurations);
        List<MavenDependency> mavenDependencies = new ArrayList<MavenDependency>();
        for (Dependency dependency : dependenciesMap.keySet()) {
            if (dependency.getArtifacts().size() == 0) {
                addFromDependencyDescriptor(mavenDependencies, pom, dependency, dependenciesMap.get(dependency));
            } else {
                addFromArtifactDescriptor(mavenDependencies, pom, dependency, dependenciesMap.get(dependency));
            }
        }
        return mavenDependencies;
    }

    private Map<Dependency, Set<String>> createDependenciesMap(Set<Configuration> configurations) {
        Map<Dependency, Set<String>> dependencySetMap = new HashMap<Dependency, Set<String>>();
        for (Configuration configuration : configurations) {
            for (Dependency dependency : configuration.getDependencies()) {
                if (dependencySetMap.get(dependency) == null) {
                    dependencySetMap.put(dependency, new HashSet<String>());
                }
                dependencySetMap.get(dependency).add(configuration.getName());
            }
        }
        return dependencySetMap;
    }

    private void addFromArtifactDescriptor(List<MavenDependency> mavenDependencies, MavenPom pom, Dependency dependency, Set<String> configurations) {
        for (DependencyArtifact artifact : dependency.getArtifacts()) {
            String scope = pom.getScopeMappings().getScope(configurations.toArray(new String[configurations.size()]));
            if (useScope(pom, scope)) {
                return;
            }
            mavenDependencies.add(createMavenDependencyFromArtifactDescriptor(dependency, artifact, scope));
        }
    }

    private void addFromDependencyDescriptor(List<MavenDependency> mavenDependencies, MavenPom pom, Dependency dependency, Set<String> configurations) {
        String scope = pom.getScopeMappings().getScope(configurations.toArray(new String[configurations.size()]));
        if (useScope(pom, scope)) {
            return;
        }
        mavenDependencies.add(createMavenDependencyFromDependencyDescriptor(dependency, scope));
    }

    private boolean useScope(MavenPom pom, String scope) {
        return scope == null && pom.getScopeMappings().isSkipUnmappedConfs();
    }

    private MavenDependency createMavenDependencyFromArtifactDescriptor(Dependency dependency, DependencyArtifact artifact, String scope) {
        return createMavenDependency(dependency, artifact.getName(), artifact.getType(), scope);
    }

    private MavenDependency createMavenDependencyFromDependencyDescriptor(Dependency dependency, String scope) {
        return createMavenDependency(dependency, dependency.getName(), null, scope);
    }

    private MavenDependency createMavenDependency(Dependency dependency, String name, String type, String scope) {
        DefaultMavenDependency mavenDependency = DefaultMavenDependency.newInstance(dependency.getGroup(), name, dependency.getVersion(), type, scope);
        addExclude(dependency, mavenDependency);
        return mavenDependency;
    }

    private void addExclude(Dependency dependency, MavenDependency mavenDependency) {
        for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
            DefaultMavenExclude mavenExclude = excludeRuleConverter.convert(excludeRule);
            if (mavenExclude != null) {
                mavenDependency.getMavenExcludes().add(mavenExclude);
            }
        }
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }
}
