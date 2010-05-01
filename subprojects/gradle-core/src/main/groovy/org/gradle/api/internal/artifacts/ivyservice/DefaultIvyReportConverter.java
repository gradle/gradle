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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ConfigurationResolveReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public IvyConversionResult convertReport(ResolveReport resolveReport, Configuration configuration) {
        Clock clock = new Clock();
        ReportConversionContext context = new ReportConversionContext(resolveReport, configuration);
        List<IvyNode> resolvedNodes = findResolvedNodes(resolveReport, context);
        for (IvyNode node : resolvedNodes) {
            constructConfigurationsForNode(node, context);
        }
        for (IvyNode node : resolvedNodes) {
            attachToParents(node, context);
        }

        if (context.root == null) {
            context.root = new DefaultResolvedDependency(resolveReport.getModuleDescriptor().getModuleRevisionId().getOrganisation(),
                    resolveReport.getModuleDescriptor().getModuleRevisionId().getName(),
                    resolveReport.getModuleDescriptor().getModuleRevisionId().getRevision(), configuration.getName(),
                    Collections.EMPTY_SET);
        }

        logger.debug("Timing: Translating report for configuration {} took {}", configuration, clock.getTime());
        return new DefaultIvyConversionResult(context.root, context.firstLevelResolvedDependencies, context.resolvedArtifacts);
    }

    private List<IvyNode> findResolvedNodes(ResolveReport resolveReport, ReportConversionContext context) {
        List<IvyNode> nodes = resolveReport.getDependencies();
        List<IvyNode> resolvedNodes = new ArrayList<IvyNode>();
        for (IvyNode node : nodes) {
            if (!isResolvedNode(node, context.conf)) {
                continue;
            }
            resolvedNodes.add(node);
        }
        if (!resolvedNodes.isEmpty()) {
            resolvedNodes.add(resolvedNodes.get(0).getRoot());
        }
        return resolvedNodes;
    }

    private boolean isResolvedNode(IvyNode node, String configuration) {
        return node.isLoaded() && !node.isEvicted(configuration);
    }

    private void attachToParents(IvyNode ivyNode, ReportConversionContext context) {
        Map<String, ConfigurationDetails> resolvedDependencies = context.handledNodes.get(ivyNode.getId());
        for (IvyNodeCallers.Caller caller : ivyNode.getCallers(context.conf)) {
            Set<String> dependencyConfigurationsForNode = getDependencyConfigurationsByCaller(ivyNode, caller);
            IvyNode parentNode = isRootCaller(context.configurationResolveReport, caller) ? ivyNode.getRoot() : context.configurationResolveReport.getDependency(caller.getModuleRevisionId());
            if (!isResolvedNode(parentNode, context.conf)) {
                continue;
            }
            Map<String, ConfigurationDetails> parentResolvedDependencies = context.handledNodes.get(parentNode.getId());
            if (parentResolvedDependencies == null) {
                throw new IllegalStateException(String.format("Could not find caller node %s for node %s. Available nodes: %s",
                        parentNode.getId(), ivyNode.getId(), context.handledNodes.keySet()));
            }
            createAssociationsBetweenChildAndParentResolvedDependencies(ivyNode, resolvedDependencies, context.resolvedArtifacts, parentNode, caller,
                    dependencyConfigurationsForNode, parentResolvedDependencies.values());
        }
    }

    private void constructConfigurationsForNode(IvyNode ivyNode, ReportConversionContext context) {
        Map<String, ConfigurationDetails> resolvedDependencies = new LinkedHashMap<String, ConfigurationDetails>();
        for (IvyNodeCallers.Caller caller : ivyNode.getCallers(context.conf)) {
            Set<String> dependencyConfigurationsForNode = getDependencyConfigurationsByCaller(ivyNode, caller);
            for (String dependencyConfiguration : dependencyConfigurationsForNode) {
                if (!resolvedDependencies.containsKey(dependencyConfiguration)) {
                    ConfigurationDetails configurationDetails = context.addConfiguration(ivyNode, dependencyConfiguration);
                    context.resolvedArtifacts.addAll(configurationDetails.dependency.getModuleArtifacts());
                    resolvedDependencies.put(dependencyConfiguration, configurationDetails);
                }
            }
        }
        if (ivyNode == ivyNode.getRoot()) {
            ConfigurationDetails rootConfiguration = resolvedDependencies.get(context.conf);
            if (rootConfiguration == null) {
                rootConfiguration = context.addConfiguration(ivyNode, context.conf);
                resolvedDependencies.put(context.conf, rootConfiguration);
            }
            context.root = rootConfiguration.dependency;
        }
        context.handledNodes.put(ivyNode.getId(), resolvedDependencies);
    }

    private void createAssociationsBetweenChildAndParentResolvedDependencies(IvyNode childNode, Map<String, ConfigurationDetails> childConfigurations,
                                                                             Set<ResolvedArtifact> resolvedArtifacts,
                                                                             IvyNode parentNode, IvyNodeCallers.Caller caller,
                                                                             Set<String> childConfigurationsToAttach,
                                                                             Collection<ConfigurationDetails> parentConfigurations) {
        for (String dependencyConfiguration : childConfigurationsToAttach) {
            Set<String> callerConfigurations = getCallerConfigurationsByDependencyConfiguration(caller, childNode, dependencyConfiguration);
            Set<ConfigurationDetails> parentCallerConfigurations = selectParentConfigurations(parentConfigurations,
                    callerConfigurations);
            for (ConfigurationDetails parentConfiguration : parentCallerConfigurations) {
                ConfigurationDetails childConfiguration = childConfigurations.get(dependencyConfiguration);
                parentConfiguration.dependency.getChildren().add(childConfiguration.dependency);
                childConfiguration.dependency.getParents().add(parentConfiguration.dependency);
                Set<ResolvedArtifact> parentSpecificResolvedArtifacts = getParentSpecificArtifacts(childConfiguration.dependency, parentConfiguration.dependency.getConfiguration(),
                        parentNode, caller, childNode);
                childConfiguration.dependency.addParentSpecificArtifacts(parentConfiguration.dependency, parentSpecificResolvedArtifacts);
                resolvedArtifacts.addAll(parentSpecificResolvedArtifacts);
            }
        }
    }

    private Set<ResolvedArtifact> getParentSpecificArtifacts(DefaultResolvedDependency resolvedDependency, String parentConfiguration, IvyNode callerNode,
                                                             IvyNodeCallers.Caller caller, IvyNode childNode) {
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

    private Set<ConfigurationDetails> selectParentConfigurations(Collection<ConfigurationDetails> parentConfigurations,
                                                                 Set<String> callerConfigurations) {
        Set<ConfigurationDetails> matchingParentConfigurations = new LinkedHashSet<ConfigurationDetails>();
        for (String callerConfiguration : callerConfigurations) {
            for (ConfigurationDetails parentConfiguration : parentConfigurations) {
                if (parentConfiguration.containsConfiguration(callerConfiguration)) {
                    matchingParentConfigurations.add(parentConfiguration);
                }
            }
        }
        return matchingParentConfigurations;
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
                dependency2CallerConfs.get(dependencyConf).add(callerConf);
            }
        }
        return dependency2CallerConfs.get(dependencyConfiguration);
    }

    private Set<String> getDependencyConfigurationsByCaller(IvyNode dependencyNode, IvyNodeCallers.Caller caller) {
        String[] dependencyConfigurations = caller.getDependencyDescriptor().getDependencyConfigurations(caller.getCallerConfigurations());
        return getRealConfigurations(dependencyNode, dependencyConfigurations);
    }

    private Set<String> getRealConfigurations(IvyNode dependencyNode, String[] dependencyConfigurations) {
        Set<String> realDependencyConfigurations = new LinkedHashSet<String>();
        for (String dependencyConfiguration : dependencyConfigurations) {
            realDependencyConfigurations.addAll(WrapUtil.toSet(dependencyNode.getRealConfs(dependencyConfiguration)));
        }
        return realDependencyConfigurations;
    }

    private Set<ResolvedArtifact> getArtifacts(IvyNode dependencyNode) {
        Set<ResolvedArtifact> resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
        Artifact[] artifacts = dependencyNode.getSelectedArtifacts(null);
        for (Artifact artifact : artifacts) {
            resolvedArtifacts.add(createResolvedArtifact(artifact, dependencyNode));
        }
        return resolvedArtifacts;
    }

    private class ReportConversionContext {
        ResolvedDependency root;
        final Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies = new LinkedHashMap<Dependency, Set<ResolvedDependency>>();
        final Map<ModuleRevisionId, Map<String, ConfigurationDetails>> handledNodes = new LinkedHashMap<ModuleRevisionId, Map<String, ConfigurationDetails>>();
        final Set<ResolvedArtifact> resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
        final ConfigurationResolveReport configurationResolveReport;
        final Map<ResolvedConfigurationIdentifier, ModuleDependency> firstLevelDependenciesModuleRevisionIds = new HashMap<ResolvedConfigurationIdentifier, ModuleDependency>();
        final Map<ResolvedConfigurationIdentifier, ConfigurationDetails> configurations = new HashMap<ResolvedConfigurationIdentifier, ConfigurationDetails>();
        final String conf;

        public ReportConversionContext(ResolveReport resolveReport, Configuration configuration) {
            configurationResolveReport = resolveReport.getConfigurationReport(configuration.getName());
            createFirstLevelDependenciesModuleRevisionIds(configuration.getAllDependencies(ModuleDependency.class));
            conf = configuration.getName();
        }

        public ConfigurationDetails addConfiguration(IvyNode ivyNode, String configuration) {
            ModuleRevisionId actualId = ivyNode.getResolvedId();
            Set<String> configurations = getConfigurationHierarchy(ivyNode, configuration);
            DefaultResolvedDependency resolvedDependency;
            if (actualId.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY) != null) {
                resolvedDependency = new DefaultResolvedDependency(
                        actualId.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY),
                        actualId.getOrganisation(), actualId.getName(), actualId.getRevision(),
                        configuration, getArtifacts(ivyNode));
            } else {
                resolvedDependency = new DefaultResolvedDependency(
                        actualId.getOrganisation(), actualId.getName(), actualId.getRevision(),
                        configuration, getArtifacts(ivyNode));
            }
            for (ResolvedArtifact resolvedArtifact : resolvedDependency.getModuleArtifacts()) {
                ((DefaultResolvedArtifact) resolvedArtifact).setResolvedDependency(resolvedDependency);
            }
            ConfigurationDetails configurationDetails = new ConfigurationDetails(resolvedDependency, ivyNode,
                    configurations);
            this.configurations.put(resolvedDependency.getId(), configurationDetails);

            // Collect top level dependencies
            ResolvedConfigurationIdentifier originalId = new ResolvedConfigurationIdentifier(ivyNode.getId(),
                    configuration);
            if (firstLevelDependenciesModuleRevisionIds.containsKey(originalId)) {
                ModuleDependency firstLevelNode = firstLevelDependenciesModuleRevisionIds.get(originalId);
                firstLevelResolvedDependencies.get(firstLevelNode).add(resolvedDependency);
            }

            return configurationDetails;
        }

        private void createFirstLevelDependenciesModuleRevisionIds(Set<ModuleDependency> firstLevelDependencies) {
            for (ModuleDependency firstLevelDependency : firstLevelDependencies) {
                ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(dependencyDescriptorFactory.createModuleRevisionId(firstLevelDependency), firstLevelDependency.getConfiguration());
                firstLevelDependenciesModuleRevisionIds.put(id, firstLevelDependency);
                firstLevelResolvedDependencies.put(firstLevelDependency, new LinkedHashSet<ResolvedDependency>());
            }
        }
    }

    private static class ConfigurationDetails {
        final DefaultResolvedDependency dependency;
        final IvyNode node;
        final Set<String> configurationHierarchy;

        private ConfigurationDetails(DefaultResolvedDependency dependency, IvyNode node,
                                     Set<String> configurationHierarchy) {
            this.dependency = dependency;
            this.node = node;
            this.configurationHierarchy = configurationHierarchy;
        }

        public boolean containsConfiguration(String configuration) {
            return configurationHierarchy.contains(configuration);
        }

        @Override
        public String toString() {
            return dependency.toString();
        }
    }
}
