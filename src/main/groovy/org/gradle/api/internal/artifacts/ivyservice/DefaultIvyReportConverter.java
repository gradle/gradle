/*
 * Copyright 2007-2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ConfigurationResolveReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedDependency;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultIvyReportConverter implements IvyReportConverter {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyReportConverter.class);

    public Set<File> getClasspath(String configuration, ResolveReport resolveReport) {
        Clock clock = new Clock();
        Set<File> classpath = new LinkedHashSet<File>();
        for (ArtifactDownloadReport artifactDownloadReport : getAllArtifactReports(resolveReport, configuration)) {
            classpath.add(artifactDownloadReport.getLocalFile());
        }
        logger.debug("Timing: Translating report for configuration {} took {}", configuration, clock.getTime());
        return classpath;
    }

    private ArtifactDownloadReport[] getAllArtifactReports(ResolveReport report, String conf) {
        logger.debug("using internal report instance to get artifacts list");
        ConfigurationResolveReport configurationReport = report.getConfigurationReport(conf);
        if (configurationReport == null) {
            throw new GradleException("bad confs provided: " + conf
                    + " not found among " + Arrays.asList(report.getConfigurations()));
        }
        return configurationReport.getArtifactsReports(null, false);
    }

    public Map<Dependency, Set<ResolvedDependency>> translateReport(ResolveReport resolveReport, Configuration configuration) {
        Clock clock = new Clock();
        Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies = new LinkedHashMap<Dependency, Set<ResolvedDependency>>();
        ConfigurationResolveReport configurationResolveReport = resolveReport.getConfigurationReport(configuration.getName());
        LinkedHashMap<ModuleRevisionId, Map<String, ResolvedDependency>> handledNodes = new LinkedHashMap<ModuleRevisionId, Map<String, ResolvedDependency>>();
        Map<ResolvedDependency, IvyNode> resolvedDependencies2Nodes = new HashMap<ResolvedDependency, IvyNode>();
        Map<ModuleRevisionId, Dependency> firstLevelDependenciesModuleRevisionIds = createFirstLevelDependenciesModuleRevisionIds(configuration.getAllDependencies());
        List nodes = resolveReport.getDependencies();
        for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
            IvyNode node = (IvyNode) iterator.next();
            if (!isResolvedNode(node, configuration)) {
                continue;
            }
            buildGraphInternal(node,
                    handledNodes,
                    resolvedDependencies2Nodes,
                    firstLevelResolvedDependencies,
                    configuration.getName(),
                    configurationResolveReport,
                    firstLevelDependenciesModuleRevisionIds,
                    resolveReport);
        }
        logger.debug("Timing: Translating report for configuration {} took {}", configuration, clock.getTime());
        return firstLevelResolvedDependencies;
    }

    private boolean isResolvedNode(IvyNode node, Configuration configuration) {
        return node.isLoaded() && !node.isEvicted(configuration.getName());
    }

    private Map<String, ResolvedDependency> buildGraphInternal(IvyNode ivyNode,
                                                               Map<ModuleRevisionId, Map<String, ResolvedDependency>> handledNodes,
                                                               Map<ResolvedDependency, IvyNode> resolvedDependencies2Nodes,
                                                               Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies, String conf,
                                                               ConfigurationResolveReport configurationResolveReport,
                                                               Map<ModuleRevisionId, Dependency> firstLevelDependenciesModuleRevisionIds, ResolveReport resolveReport) {
        ModuleRevisionId nodeId = ivyNode.getId();
        if (handledNodes.containsKey(nodeId)) {
            return handledNodes.get(nodeId);
        }
        Map<String, ResolvedDependency> resolvedDependencies = new LinkedHashMap<String, ResolvedDependency>();
        for (IvyNodeCallers.Caller caller : ivyNode.getCallers(conf)) {
            Set<String> dependencyConfigurations = getDependencyConfigurations(ivyNode, caller);
            for (String dependencyConfiguration : dependencyConfigurations) {
                if (resolvedDependencies.containsKey(dependencyConfiguration)) {
                    continue;
                }
                DefaultResolvedDependency resolvedDependency = createResolvedDependency(ivyNode, resolveReport, dependencyConfiguration);
                resolvedDependencies.put(dependencyConfiguration, resolvedDependency);
                resolvedDependencies2Nodes.put(resolvedDependency, ivyNode);
                addNodeIfFirstLevelDependency(ivyNode, resolvedDependency, firstLevelDependenciesModuleRevisionIds, firstLevelResolvedDependencies);
            }
            if (caller.getModuleDescriptor().equals(configurationResolveReport.getModuleDescriptor())) {
                continue;
            }
            IvyNode parent = configurationResolveReport.getDependency(caller.getModuleRevisionId());
            Map<String, ResolvedDependency> parentDeps = buildGraphInternal(parent, handledNodes, resolvedDependencies2Nodes,
                    firstLevelResolvedDependencies, conf, configurationResolveReport,
                    firstLevelDependenciesModuleRevisionIds, resolveReport);
            for (String dependencyConfiguration : dependencyConfigurations) {
                Set<String> callerConfigurations = callerConfForDependencyConf(caller, ivyNode, dependencyConfiguration);
                Set<ResolvedDependency> parentResolvedDependenciesSubSet = getParentResolvedDependencies(resolvedDependencies2Nodes,
                        parentDeps, callerConfigurations);
                for (ResolvedDependency parentResolvedDependency : parentResolvedDependenciesSubSet) {
                    ResolvedDependency resolvedDependency = resolvedDependencies.get(dependencyConfiguration);
                    parentResolvedDependency.getChildren().add(resolvedDependency);
                    resolvedDependency.getParents().add(parentResolvedDependency);
                }
            }
        }
        handledNodes.put(nodeId, resolvedDependencies);
        return resolvedDependencies;
    }

    private Set<ResolvedDependency> getParentResolvedDependencies(Map<ResolvedDependency, IvyNode> resolvedDependencies2Nodes, Map<String, ResolvedDependency> parentResolvedDependencies,
                                                                  Set<String> callerConfigurations) {
        Set<ResolvedDependency> parentResolvedDependenciesSubSet = new LinkedHashSet<ResolvedDependency>();
        for (String callerConfiguration : callerConfigurations) {
            if (parentResolvedDependencies.containsKey(callerConfiguration)) {
                parentResolvedDependenciesSubSet.add(parentResolvedDependencies.get(callerConfiguration));
                continue;
            }
            for (ResolvedDependency resolvedDependency : parentResolvedDependencies.values()) {
                IvyNode ivyNode = resolvedDependencies2Nodes.get(resolvedDependency);
                String resolvedDependencyConfigurationName = resolvedDependency.getConfiguration();
                if (getConfigurationHierarchy(ivyNode, resolvedDependencyConfigurationName).contains(callerConfiguration)) {
                    parentResolvedDependenciesSubSet.add(resolvedDependency);
                }
            }
        }
        return parentResolvedDependenciesSubSet;
    }

    private Set<String> getConfigurationHierarchy(IvyNode node, String configurationName) {
        Set<String> configurations = new HashSet<String>();
        configurations.add(configurationName);
        org.apache.ivy.core.module.descriptor.Configuration configuration = node.getConfiguration(configurationName);
        for (String extendedConfigurationNames : configuration.getExtends()) {
            configurations.addAll(getConfigurationHierarchy(node, extendedConfigurationNames));
        }
        return configurations;
    }

    private Set<String> callerConfForDependencyConf(IvyNodeCallers.Caller caller, IvyNode dependencyNode, String dependencyConfiguration) {
        Map<String, Set<String>> dependency2CallerConfs = new LinkedHashMap<String, Set<String>>();
        for (String callerConf : caller.getCallerConfigurations()) {
            Set<String> dependencyConfs = getRealConfigurations(dependencyNode
                    , caller.getDependencyDescriptor().getDependencyConfigurations(callerConf));
            for (String dependencyConf : dependencyConfs) {
                if (!dependency2CallerConfs.containsKey(dependencyConf)) {
                    dependency2CallerConfs.put(dependencyConf, new LinkedHashSet<String>());
                }
                dependency2CallerConfs.get(dependencyConf).add(callerConf.toString());
            }
        }
        return dependency2CallerConfs.get(dependencyConfiguration);
    }

    private Set<String> getDependencyConfigurations(IvyNode dependencyNode, IvyNodeCallers.Caller caller) {
        String[] dependencyConfigurations = caller.getDependencyDescriptor().getDependencyConfigurations(caller.getCallerConfigurations());
        Set<String> realDependencyConfigurations = getRealConfigurations(dependencyNode, dependencyConfigurations);
        return realDependencyConfigurations;
    }

    private Set<String> getRealConfigurations(IvyNode dependencyNode, String[] dependencyConfigurations) {
        Set<String> realDependencyConfigurations = new LinkedHashSet<String>();
        for (String dependencyConfiguration : dependencyConfigurations) {
            realDependencyConfigurations.addAll(WrapUtil.toSet(dependencyNode.getRealConfs(dependencyConfiguration)));
        }
        return realDependencyConfigurations;
    }

    private DefaultResolvedDependency createResolvedDependency(IvyNode ivyNode, ResolveReport resolveReport, String configuration) {
        ModuleRevisionId moduleRevisionId = ivyNode.getId();
        DefaultResolvedDependency resolvedDependency = new DefaultResolvedDependency(
                moduleRevisionId.getOrganisation() + ":" +
                        moduleRevisionId.getName() + ":" +
                        moduleRevisionId.getRevision(),
                configuration,
                getFilesForReport(ivyNode, resolveReport.getArtifactsReports(ivyNode.getId()), configuration));
        return resolvedDependency;
    }

    private Set<File> getFilesForReport(IvyNode dependencyNode, ArtifactDownloadReport[] artifactDownloadReports, String configuration) {
        Set<Artifact> artifacts = getArtifactsForConfiguration(dependencyNode, configuration);
        Set<File> files = new LinkedHashSet<File>();
        if (artifactDownloadReports != null) {
            for (ArtifactDownloadReport artifactDownloadReport : artifactDownloadReports) {
                if (artifacts.contains(artifactDownloadReport.getArtifact()))
                    files.add(artifactDownloadReport.getLocalFile());
            }
        }
        return files;
    }

    private Set<Artifact> getArtifactsForConfiguration(IvyNode dependencyNode, String configuration) {
        Set<Artifact> artifacts = new HashSet<Artifact>();
        Set<String> configurations = getConfigurationHierarchy(dependencyNode, configuration);
        for (String hierarchyConfiguration : configurations) {
            Artifact[] artifactSubSet = dependencyNode.getDescriptor().getArtifacts(hierarchyConfiguration);
            for (Artifact artifact : artifactSubSet) {
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    private Map<ModuleRevisionId, Dependency> createFirstLevelDependenciesModuleRevisionIds(Set<Dependency> firstLevelDependencies) {
        Map<ModuleRevisionId, Dependency> firstLevelDependenciesModuleRevisionIds = new LinkedHashMap<ModuleRevisionId, Dependency>();
        for (Dependency firstLevelDependency : firstLevelDependencies) {
            ModuleRevisionId moduleRevisionId = ModuleRevisionId.newInstance(
                    GUtil.elvis(firstLevelDependency.getGroup(), ""),
                    firstLevelDependency.getName(),
                    GUtil.elvis(firstLevelDependency.getVersion(), ""));
            firstLevelDependenciesModuleRevisionIds.put(moduleRevisionId, firstLevelDependency);
        }
        return firstLevelDependenciesModuleRevisionIds;
    }

    private void addNodeIfFirstLevelDependency(IvyNode ivyNode, DefaultResolvedDependency resolvedDependency,
                                               Map<ModuleRevisionId, Dependency> firstLevelDependencies2Nodes,
                                               Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies) {
        ModuleRevisionId normalizedNodeId = normalize(ivyNode.getId());
        if (firstLevelDependencies2Nodes.containsKey(normalizedNodeId)) {
            Dependency firstLevelNode = firstLevelDependencies2Nodes.get(normalizedNodeId);
            if (!firstLevelResolvedDependencies.containsKey(firstLevelNode)) {
                firstLevelResolvedDependencies.put(firstLevelNode, new LinkedHashSet<ResolvedDependency>());
            }
            firstLevelResolvedDependencies.get(firstLevelNode).add(resolvedDependency);
        }
    }

    /*
    * Gradle has a different notion of equality then Ivy. We need to map the download reports to
    * moduleRevisionIds that are only use fields relevant for Gradle equality.
    */
    private ModuleRevisionId normalize(ModuleRevisionId moduleRevisionId) {
        return ModuleRevisionId.newInstance(
                moduleRevisionId.getOrganisation(),
                moduleRevisionId.getName(),
                moduleRevisionId.getRevision());
    }
}
