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
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ConfigurationResolveReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptorFactory;
import org.gradle.util.Clock;
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

    private DependencyDescriptorFactory dependencyDescriptorFactory;

    public DefaultIvyReportConverter(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }

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

    public IvyConversionResult convertReport(ResolveReport resolveReport, Configuration configuration) {
        Clock clock = new Clock();
        Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies = new LinkedHashMap<Dependency, Set<ResolvedDependency>>();
        Set<ResolvedArtifact> resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
        ConfigurationResolveReport configurationResolveReport = resolveReport.getConfigurationReport(configuration.getName());
        LinkedHashMap<ModuleRevisionId, Map<String, DefaultResolvedDependency>> handledNodes = new LinkedHashMap<ModuleRevisionId, Map<String, DefaultResolvedDependency>>();
        Map<DefaultResolvedDependency, IvyNode> resolvedDependencies2Nodes = new HashMap<DefaultResolvedDependency, IvyNode>();
        Map<ModuleRevisionId, Map<String, ModuleDependency>> firstLevelDependenciesModuleRevisionIds = createFirstLevelDependenciesModuleRevisionIds(configuration.getAllDependencies(ModuleDependency.class));
        List nodes = resolveReport.getDependencies();
        for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
            IvyNode node = (IvyNode) iterator.next();
            if (!isResolvedNode(node, configuration)) {
                continue;
            }
            getResolvedDependenciesForNode(node,
                    handledNodes,
                    resolvedDependencies2Nodes,
                    firstLevelResolvedDependencies,
                    resolvedArtifacts,
                    configuration.getName(),
                    configurationResolveReport,
                    firstLevelDependenciesModuleRevisionIds,
                    resolveReport);
        }
        logger.debug("Timing: Translating report for configuration {} took {}", configuration, clock.getTime());
        return new DefaultIvyConversionResult(firstLevelResolvedDependencies, resolvedArtifacts);
    }

    private boolean isResolvedNode(IvyNode node, Configuration configuration) {
        return node.isLoaded() && !node.isEvicted(configuration.getName());
    }

    private Map<String, DefaultResolvedDependency> getResolvedDependenciesForNode(IvyNode ivyNode,
                                                                                  Map<ModuleRevisionId, Map<String, DefaultResolvedDependency>> handledNodes,
                                                                                  Map<DefaultResolvedDependency, IvyNode> resolvedDependencies2Nodes,
                                                                                  Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies, Set<ResolvedArtifact> resolvedArtifacts, String conf,
                                                                                  ConfigurationResolveReport configurationResolveReport,
                                                                                  Map<ModuleRevisionId, Map<String, ModuleDependency>> firstLevelDependenciesModuleRevisionIds, ResolveReport resolveReport) {
        ModuleRevisionId nodeId = ivyNode.getId();
        if (handledNodes.containsKey(nodeId)) {
            return handledNodes.get(nodeId);
        }
        Map<String, DefaultResolvedDependency> resolvedDependencies = new LinkedHashMap<String, DefaultResolvedDependency>();
        for (IvyNodeCallers.Caller caller : ivyNode.getCallers(conf)) {
            Set<String> dependencyConfigurationsForNode = getDependencyConfigurationsByCaller(ivyNode, caller);
            for (String dependencyConfiguration : dependencyConfigurationsForNode) {
                DefaultResolvedDependency resolvedDependencyForDependencyConfiguration = resolvedDependencies.get(dependencyConfiguration);
                if (resolvedDependencyForDependencyConfiguration == null) {
                    resolvedDependencyForDependencyConfiguration = createResolvedDependency(ivyNode, resolveReport, dependencyConfiguration);
                    resolvedArtifacts.addAll(resolvedDependencyForDependencyConfiguration.getModuleArtifacts());
                    resolvedDependencies.put(dependencyConfiguration, resolvedDependencyForDependencyConfiguration);
                    resolvedDependencies2Nodes.put(resolvedDependencyForDependencyConfiguration, ivyNode);
                    addNodeIfFirstLevelDependency(ivyNode, resolvedDependencyForDependencyConfiguration, firstLevelDependenciesModuleRevisionIds, firstLevelResolvedDependencies);
                }
            }
            if (isRootCaller(configurationResolveReport, caller)) {
                for (DefaultResolvedDependency resolvedDependency : resolvedDependencies.values()) {
                    resolvedDependency.getParents().add(null);
                    resolvedDependency.addParentSpecificArtifacts(null, getParentSpecificArtifacts(resolvedDependency, conf, ivyNode.getRoot(), caller, ivyNode, resolveReport));
                }
                continue;
            }
            IvyNode parentNode = configurationResolveReport.getDependency(caller.getModuleRevisionId());
            Map<String, DefaultResolvedDependency> parentResolvedDependencies = getResolvedDependenciesForNode(parentNode, handledNodes, resolvedDependencies2Nodes,
                    firstLevelResolvedDependencies, resolvedArtifacts, conf, configurationResolveReport,
                    firstLevelDependenciesModuleRevisionIds, resolveReport);
            createAssociationsBetweenChildAndParentResolvedDependencies(ivyNode, resolvedDependencies, resolvedArtifacts, parentNode, caller,
                    dependencyConfigurationsForNode, parentResolvedDependencies, resolveReport);
        }
        handledNodes.put(nodeId, resolvedDependencies);
        return resolvedDependencies;
    }

    private void createAssociationsBetweenChildAndParentResolvedDependencies(IvyNode ivyNode, Map<String, DefaultResolvedDependency> resolvedDependencies,
                                                                             Set<ResolvedArtifact> resolvedArtifacts, IvyNode callerNode, IvyNodeCallers.Caller caller, Set<String> dependencyConfigurationsForNode, Map<String, DefaultResolvedDependency> parentResolvedDependencies, ResolveReport resolveReport) {
        for (String dependencyConfiguration : dependencyConfigurationsForNode) {
            Set<String> callerConfigurations = getCallerConfigurationsByDependencyConfiguration(caller, ivyNode, dependencyConfiguration);
            Set<DefaultResolvedDependency> parentResolvedDependenciesForCallerConfigurations = getParentResolvedDependenciesByConfigurations(
                    parentResolvedDependencies,
                    callerConfigurations);
            for (DefaultResolvedDependency parentResolvedDependency : parentResolvedDependenciesForCallerConfigurations) {
                DefaultResolvedDependency resolvedDependency = resolvedDependencies.get(dependencyConfiguration);
                parentResolvedDependency.getChildren().add(resolvedDependency);
                resolvedDependency.getParents().add(parentResolvedDependency);
                Set<ResolvedArtifact> parentSpecificResolvedArtifacts = getParentSpecificArtifacts(resolvedDependency, parentResolvedDependency.getConfiguration(),
                        callerNode, caller, ivyNode, resolveReport);
                resolvedDependency.addParentSpecificArtifacts(parentResolvedDependency, parentSpecificResolvedArtifacts);
                resolvedArtifacts.addAll(parentSpecificResolvedArtifacts);
            }
        }
    }

    private Set<ResolvedArtifact> getParentSpecificArtifacts(DefaultResolvedDependency resolvedDependency, String parentConfiguration, IvyNode callerNode, IvyNodeCallers.Caller caller, IvyNode childNode, ResolveReport resolveReport) {
        Set<String> parentConfigurations = getConfigurationHierarchy(callerNode, parentConfiguration);
        Set<DependencyArtifactDescriptor> parentArtifacts = new LinkedHashSet<DependencyArtifactDescriptor>();
        for (String configuration : parentConfigurations) {
            parentArtifacts.addAll(WrapUtil.toSet(caller.getDependencyDescriptor().getDependencyArtifacts(configuration)));
        }

        Artifact[] allArtifacts = childNode.getSelectedArtifacts(null);
        Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
        for (Artifact artifact : allArtifacts) {
            for (DependencyArtifactDescriptor parentArtifact : parentArtifacts) {
                if (isEquals(parentArtifact, artifact)) {
                    DefaultResolvedArtifact resolvedArtifact = createResolvedArtifact(artifact, childNode);
                    resolvedArtifact.setResolvedDependency(resolvedDependency);
                    artifacts.add(resolvedArtifact);
                    break;
                }
            }
        }
        return artifacts;
    }

    private DefaultResolvedArtifact createResolvedArtifact(Artifact artifact, IvyNode ivyNode) {
        return new DefaultResolvedArtifact(artifact, ivyNode.getData().getEngine());
    }

    private boolean isEquals(DependencyArtifactDescriptor parentArtifact, Artifact artifact) {
        return parentArtifact.getName().equals(artifact.getName())
                && parentArtifact.getExt().equals(artifact.getExt())
                && parentArtifact.getType().equals(artifact.getType())
                && parentArtifact.getQualifiedExtraAttributes().equals(artifact.getQualifiedExtraAttributes());
    }

    private boolean isRootCaller(ConfigurationResolveReport configurationResolveReport, IvyNodeCallers.Caller caller) {
        return caller.getModuleDescriptor().equals(configurationResolveReport.getModuleDescriptor());
    }

    private Set<DefaultResolvedDependency> getParentResolvedDependenciesByConfigurations(Map<String, DefaultResolvedDependency> parentResolvedDependencies,
                                                                                         Set<String> callerConfigurations) {
        Set<DefaultResolvedDependency> parentResolvedDependenciesSubSet = new LinkedHashSet<DefaultResolvedDependency>();
        for (String callerConfiguration : callerConfigurations) {
            for (DefaultResolvedDependency parentResolvedDependency : parentResolvedDependencies.values()) {
                if (parentResolvedDependency.containsConfiguration(callerConfiguration)) {
                    parentResolvedDependenciesSubSet.add(parentResolvedDependency);
                }
            }
        }
        return parentResolvedDependenciesSubSet;
    }

    private Set<String> getConfigurationHierarchy(IvyNode node, String configurationName) {
        Set<String> configurations = new LinkedHashSet<String>();
        configurations.add(configurationName);
        org.apache.ivy.core.module.descriptor.Configuration configuration = node.getConfiguration(configurationName);
        for (String extendedConfigurationNames : configuration.getExtends()) {
            configurations.addAll(getConfigurationHierarchy(node, extendedConfigurationNames));
        }
        return configurations;
    }

    private Set<String> getCallerConfigurationsByDependencyConfiguration(IvyNodeCallers.Caller caller, IvyNode dependencyNode, String dependencyConfiguration) {
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

    private Set<String> getDependencyConfigurationsByCaller(IvyNode dependencyNode, IvyNodeCallers.Caller caller) {
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
        Set<String> configurations = getConfigurationHierarchy(ivyNode, configuration);
        DefaultResolvedDependency resolvedDependency;
        if (moduleRevisionId.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY) != null) {
            resolvedDependency = new DefaultResolvedDependency(
                    moduleRevisionId.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY),
                    moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision(),
                    configuration,
                    configurations,
                    getArtifacts(ivyNode));
        } else {
            resolvedDependency = new DefaultResolvedDependency(
                    moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision(),
                    configuration,
                    configurations,
                    getArtifacts(ivyNode));
        }
        for (ResolvedArtifact resolvedArtifact : resolvedDependency.getModuleArtifacts()) {
            ((DefaultResolvedArtifact) resolvedArtifact).setResolvedDependency(resolvedDependency);
        }
        return resolvedDependency;
    }

    private Set<ResolvedArtifact> getArtifacts(IvyNode dependencyNode) {
        Set<ResolvedArtifact> resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
        Artifact[] artifacts = dependencyNode.getSelectedArtifacts(null);
        for (Artifact artifact : artifacts) {
            resolvedArtifacts.add(createResolvedArtifact(artifact, dependencyNode));
        }
        return resolvedArtifacts;
    }

    private Map<ModuleRevisionId, Map<String, ModuleDependency>> createFirstLevelDependenciesModuleRevisionIds(Set<ModuleDependency> firstLevelDependencies) {
        Map<ModuleRevisionId, Map<String, ModuleDependency>> firstLevelDependenciesModuleRevisionIds =
                new LinkedHashMap<ModuleRevisionId, Map<String, ModuleDependency>>();
        for (ModuleDependency firstLevelDependency : firstLevelDependencies) {
            ModuleRevisionId moduleRevisionId = normalize(dependencyDescriptorFactory.createModuleRevisionId(firstLevelDependency));
            if (firstLevelDependenciesModuleRevisionIds.get(moduleRevisionId) == null) {
                firstLevelDependenciesModuleRevisionIds.put(moduleRevisionId, new LinkedHashMap<String, ModuleDependency>());
            }
            firstLevelDependenciesModuleRevisionIds.get(moduleRevisionId).put(firstLevelDependency.getConfiguration(), firstLevelDependency);
        }
        return firstLevelDependenciesModuleRevisionIds;
    }

    private void addNodeIfFirstLevelDependency(IvyNode ivyNode, DefaultResolvedDependency resolvedDependency,
                                               Map<ModuleRevisionId, Map<String, ModuleDependency>> firstLevelDependencies2Nodes,
                                               Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies) {
        ModuleRevisionId normalizedNodeId = normalize(ivyNode.getId());
        if (firstLevelDependencies2Nodes.containsKey(normalizedNodeId)) {
            ModuleDependency firstLevelNode = firstLevelDependencies2Nodes.get(normalizedNodeId).get(resolvedDependency.getConfiguration());
            if (firstLevelNode == null) {
                return;
            }
            if (!firstLevelResolvedDependencies.containsKey(firstLevelNode)) {
                firstLevelResolvedDependencies.put(firstLevelNode, new LinkedHashSet<ResolvedDependency>());
            }
            firstLevelResolvedDependencies.get(firstLevelNode).add(resolvedDependency);
        }
    }

    /*
    * GradleLauncher has a different notion of equality then Ivy. We need to map the download reports to
    * moduleRevisionIds that are only use fields relevant for GradleLauncher equality.
    */
    private ModuleRevisionId normalize(ModuleRevisionId moduleRevisionId) {
        return ModuleRevisionId.newInstance(
                moduleRevisionId.getOrganisation(),
                moduleRevisionId.getName(),
                moduleRevisionId.getRevision());
    }
}
