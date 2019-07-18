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
package org.gradle.api.publication.maven.internal.pom;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publication.maven.internal.VersionRangeMapper;

import java.util.*;

import static com.google.common.base.Strings.emptyToNull;

class DefaultPomDependenciesConverter implements PomDependenciesConverter {
    private static final List<Exclusion> EXCLUDE_ALL = initExcludeAll();
    private ExcludeRuleConverter excludeRuleConverter;
    private VersionRangeMapper versionRangeMapper;

    public DefaultPomDependenciesConverter(ExcludeRuleConverter excludeRuleConverter, VersionRangeMapper versionRangeMapper) {
        this.excludeRuleConverter = excludeRuleConverter;
        this.versionRangeMapper = versionRangeMapper;
    }

    @Override
    public List<Dependency> convert(Conf2ScopeMappingContainer conf2ScopeMappingContainer, Set<Configuration> configurations) {
        Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations = createDependencyToConfigurationsMap(configurations);
        Map<ModuleDependency, Conf2ScopeMapping> dependenciesMap = createDependencyToScopeMap(conf2ScopeMappingContainer, dependencyToConfigurations);
        Map<Dependency, Integer> dependenciesWithPriorities = new LinkedHashMap<Dependency, Integer>();
        for (ModuleDependency dependency : dependenciesMap.keySet()) {
            Conf2ScopeMapping conf2ScopeMapping = dependenciesMap.get(dependency);
            String scope = conf2ScopeMapping.getScope();
            Integer priority = conf2ScopeMapping.getPriority() == null ? 0 : conf2ScopeMapping.getPriority();
            Set<Configuration> dependencyConfigurations = dependencyToConfigurations.get(dependency);
            if (dependency.getArtifacts().size() == 0) {
                addFromDependencyDescriptor(dependenciesWithPriorities, dependency, scope, priority, dependencyConfigurations);
            } else {
                addFromArtifactDescriptor(dependenciesWithPriorities, dependency, scope, priority, dependencyConfigurations);
            }
        }
        return new ArrayList<Dependency>(dependenciesWithPriorities.keySet());
    }

    private Map<ModuleDependency, Conf2ScopeMapping> createDependencyToScopeMap(Conf2ScopeMappingContainer conf2ScopeMappingContainer,
                                                                                Map<ModuleDependency, Set<Configuration>> dependencyToConfigurations) {
        Map<ModuleDependency, Conf2ScopeMapping> dependencyToScope = new LinkedHashMap<ModuleDependency, Conf2ScopeMapping>();
        for (ModuleDependency dependency : dependencyToConfigurations.keySet()) {
            Conf2ScopeMapping conf2ScopeDependencyMapping = conf2ScopeMappingContainer.getMapping(dependencyToConfigurations.get(dependency));
            if (!useScope(conf2ScopeMappingContainer, conf2ScopeDependencyMapping)) {
                continue;
            }
            dependencyToScope.put(findDependency(dependency, conf2ScopeDependencyMapping.getConfiguration()), conf2ScopeDependencyMapping);
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
        Map<ModuleDependency, Set<Configuration>> dependencySetMap = new LinkedHashMap<ModuleDependency, Set<Configuration>>();
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

    private void addFromArtifactDescriptor(Map<Dependency, Integer> dependenciesPriorityMap,
                                           ModuleDependency dependency, String scope, Integer priority,
                                           Set<Configuration> configurations) {
        for (DependencyArtifact artifact : dependency.getArtifacts()) {
            addMavenDependencies(dependenciesPriorityMap, dependency, artifact.getName(), artifact.getType(), scope, artifact.getClassifier(), priority, configurations);
        }
    }

    private void addFromDependencyDescriptor(Map<Dependency, Integer> dependenciesPriorityMap,
                                             ModuleDependency dependency, String scope, Integer priority,
                                             Set<Configuration> configurations) {
        addMavenDependencies(dependenciesPriorityMap, dependency, dependency.getName(), null, scope, null, priority, configurations);
    }

    private static Configuration getTargetConfiguration(ProjectDependency dependency) {
        // todo CC: check that it ok to do this if configurations have attributes
        String targetConfiguration = dependency.getTargetConfiguration();
        if (targetConfiguration == null) {
            targetConfiguration = org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION;
        }
        return dependency.getDependencyProject().getConfigurations().getByName(targetConfiguration);
    }

    private void addMavenDependencies(Map<Dependency, Integer> dependenciesWithPriorities,
                                      ModuleDependency dependency, String name, String type, String scope, String classifier, Integer priority,
                                      Set<Configuration> configurations) {
        final List<Dependency> mavenDependencies = new ArrayList<Dependency>();

        if (dependency instanceof ProjectDependency) {
            // TODO: Combine with ProjectDependencyPublicationResolver
            final ProjectDependency projectDependency = (ProjectDependency) dependency;
            ((ProjectInternal)projectDependency.getDependencyProject()).getMutationState().withMutableState(new Runnable() {
                @Override
                public void run() {
                    String artifactId = determineProjectDependencyArtifactId(projectDependency);

                    Configuration dependencyConfig = getTargetConfiguration(projectDependency);

                    for (PublishArtifact artifactToPublish : dependencyConfig.getAllArtifacts()) {
                        Dependency mavenDependency = new Dependency();
                        mavenDependency.setArtifactId(artifactId);
                        if (artifactToPublish.getClassifier() != null && !artifactToPublish.getClassifier().equals("")) {
                            mavenDependency.setClassifier(artifactToPublish.getClassifier());
                        }
                        mavenDependencies.add(mavenDependency);
                    }
                }
            });
        } else {
            Dependency mavenDependency = new Dependency();
            mavenDependency.setArtifactId(name);
            mavenDependency.setClassifier(classifier);
            mavenDependencies.add(mavenDependency);
        }

        for (Dependency mavenDependency : mavenDependencies) {
            mavenDependency.setGroupId(dependency.getGroup());
            mavenDependency.setVersion(mapToMavenSyntax(dependency.getVersion()));
            mavenDependency.setType(type);
            mavenDependency.setScope(scope);
            mavenDependency.setExclusions(getExclusions(dependency, configurations));
            // Dependencies de-duplication
            Optional<Dependency> duplicateDependency = findEqualIgnoreScopeVersionAndExclusions(dependenciesWithPriorities.keySet(), mavenDependency);
            if (!duplicateDependency.isPresent()) {
                // Add if absent
                dependenciesWithPriorities.put(mavenDependency, priority);
            } else {
                // Use highest version on highest scope, keep highest scope exclusions only
                int duplicatePriority = dependenciesWithPriorities.get(duplicateDependency.get());
                boolean samePriority = priority == duplicatePriority;
                boolean higherPriority = priority > duplicatePriority;
                boolean higherVersion = compareMavenVersionStrings(mavenDependency.getVersion(), duplicateDependency.get().getVersion()) > 0;
                if (higherPriority || higherVersion) {
                    // Replace if higher priority or version with highest priority and version
                    dependenciesWithPriorities.remove(duplicateDependency.get());
                    if (!higherPriority) {
                        // Lower or equal priority but higher version, keep higher scope and exclusions
                        mavenDependency.setScope(duplicateDependency.get().getScope());
                        if (!samePriority) {
                            mavenDependency.setExclusions(duplicateDependency.get().getExclusions());
                        }
                    }
                    int highestPriority = higherPriority ? priority : duplicatePriority;
                    dependenciesWithPriorities.put(mavenDependency, highestPriority);
                }
            }
        }
    }

    private int compareMavenVersionStrings(String dependencyVersionString, String duplicateVersionString) {
        String dependencyVersion = emptyToNull(dependencyVersionString);
        String duplicateVersion = emptyToNull(duplicateVersionString);
        if (dependencyVersion == null && duplicateVersion == null) {
            return 0;
        }
        if (dependencyVersion == null) {
            return -1;
        }
        if (duplicateVersion == null) {
            return 1;
        }
        ArtifactVersion dependencyArtifactVersion = new DefaultArtifactVersion(dependencyVersion);
        ArtifactVersion duplicateArtifactVersion = new DefaultArtifactVersion(duplicateVersion);
        return dependencyArtifactVersion.compareTo(duplicateArtifactVersion);
    }

    private Optional<Dependency> findEqualIgnoreScopeVersionAndExclusions(Collection<Dependency> dependencies, Dependency candidate) {
        // For project dependencies de-duplication
        // Ignore scope on purpose
        // Ignore version because Maven doesn't support dependencies with different versions on different scopes
        // Ignore exclusions because we don't know how to choose/merge them
        // Consequence is that we use the highest version and the exclusions of highest priority dependency when de-duplicating
        // Use Maven Dependency "Management Key" as discriminator: groupId:artifactId:type:classifier
        final String candidateManagementKey = candidate.getManagementKey();
        return Iterables.tryFind(dependencies, new Predicate<Dependency>() {
            @Override
            public boolean apply(Dependency dependency) {
                return dependency.getManagementKey().equals(candidateManagementKey);
            }
        });
    }

    private String mapToMavenSyntax(String version) {
        return versionRangeMapper.map(version);
    }

    protected String determineProjectDependencyArtifactId(ProjectDependency dependency) {
        return new ProjectDependencyArtifactIdExtractorHack(dependency).extract();
    }

    private List<Exclusion> getExclusions(ModuleDependency dependency, Set<Configuration> configurations) {
        if (!dependency.isTransitive()) {
            return EXCLUDE_ALL;
        }
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

    private static List<Exclusion> initExcludeAll() {
        Exclusion excludeAll = new Exclusion();
        excludeAll.setGroupId("*");
        excludeAll.setArtifactId("*");
        return Collections.singletonList(excludeAll);
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }
}
