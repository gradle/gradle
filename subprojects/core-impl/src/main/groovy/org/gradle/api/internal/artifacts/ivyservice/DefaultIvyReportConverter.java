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
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ConfigurationResolveReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeUsage;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.util.Clock;
import org.gradle.util.UncheckedException;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultIvyReportConverter implements IvyReportConverter {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyReportConverter.class);

    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final ResolvedArtifactFactory artifactFactory;

    public DefaultIvyReportConverter(DependencyDescriptorFactory dependencyDescriptorFactory, ResolvedArtifactFactory artifactFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.artifactFactory = artifactFactory;
    }

    public IvyConversionResult convertReport(ResolveReport resolveReport, Configuration configuration) {
        Clock clock = new Clock();
        ReportConversionContext context = new ReportConversionContext(resolveReport, configuration);
        List<IvyNode> resolvedNodes = findResolvedNodes(resolveReport, context);

        for (IvyNode node : resolvedNodes) {
            createConfigurations(node, context);
        }
        for (IvyNode node : resolvedNodes) {
            attachToParents(node, context);
        }

        if (context.root == null) {
            context.root = new DefaultResolvedDependency(resolveReport.getModuleDescriptor().getModuleRevisionId().getOrganisation(),
                    resolveReport.getModuleDescriptor().getModuleRevisionId().getName(),
                    resolveReport.getModuleDescriptor().getModuleRevisionId().getRevision(), configuration.getName());
        }

        logger.debug("Timing: Translating report for {} took {}", configuration, clock.getTime());
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

    private void createConfigurations(IvyNode node, ReportConversionContext context) {
        Collection<Usage> usages = getUsages(node, context);
        for (Usage usage : usages) {
            Set<String> childConfigs = getRealConfigurations(node, Arrays.asList(usage.dependencyDescriptor.getDependencyConfigurations(usage.dependerConfig)));
            for (String childConfigName : childConfigs) {
                context.addConfiguration(node, childConfigName);
            }
        }
        if (node == node.getRoot()) {
            context.root = context.addConfiguration(node, context.conf).dependency;
        }
    }

    private void attachToParents(IvyNode childNode, ReportConversionContext context) {
        Collection<Usage> usages = getUsages(childNode, context);
        for (Usage usage : usages) {
            ModuleRevisionId parentId = usage.dependencyDescriptor.getParentRevisionId();
            Set<ConfigurationDetails> parentConfigurations;
            if (parentId.equals(context.configurationResolveReport.getModuleDescriptor().getModuleRevisionId())) {
                parentConfigurations = context.findExtendingConfigurations(parentId, context.conf);
            } else {
                parentConfigurations = context.findExtendingConfigurations(parentId, usage.dependerConfig);
            }
            Set<String> childConfigs = getRealConfigurations(childNode, Arrays.asList(usage.dependencyDescriptor.getDependencyConfigurations(usage.dependerConfig)));
            for (ConfigurationDetails parentConfiguration : parentConfigurations) {
                for (String childConfigName : childConfigs) {
                    ConfigurationDetails childConfiguration = context.getConfiguration(childNode, childConfigName);
                    parentConfiguration.dependency.addChild(childConfiguration.dependency);
                    Set<ResolvedArtifact> parentSpecificResolvedArtifacts = getParentSpecificArtifacts(childNode, childConfiguration, usage);
                    childConfiguration.dependency.addParentSpecificArtifacts(parentConfiguration.dependency, parentSpecificResolvedArtifacts);
                    context.resolvedArtifacts.addAll(parentSpecificResolvedArtifacts);
                }
            }
        }
    }

    private Set<ResolvedArtifact> getParentSpecificArtifacts(IvyNode childNode, ConfigurationDetails child, Usage usage) {
        DependencyDescriptor dependencyDescriptor = usage.dependencyDescriptor;
        Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

        // Add in the artifacts requested in the dependency descriptor
        for (DependencyArtifactDescriptor artifactDescriptor : dependencyDescriptor.getDependencyArtifacts(usage.dependerConfig)) {
            MDArtifact artifact = new MDArtifact(childNode.getDescriptor(), artifactDescriptor.getName(), artifactDescriptor.getType(), artifactDescriptor.getExt(), artifactDescriptor.getUrl(), artifactDescriptor.getQualifiedExtraAttributes());
            artifacts.add(createResolvedArtifact(child.dependency, artifact, childNode));
        }

        // Add in the default artifacts of the target configuration if required
        if (artifacts.isEmpty()) {
            for (String childConfig : child.configurationHierarchy) {
                for (Artifact artifact : childNode.getDescriptor().getArtifacts(childConfig)) {
                    artifacts.add(createResolvedArtifact(child.dependency, artifact, childNode));
                }
            }
        }
        return artifacts;
    }

    private ResolvedArtifact createResolvedArtifact(ResolvedDependency owner, Artifact artifact, IvyNode ivyNode) {
        return artifactFactory.create(owner, artifact, ivyNode.getData().getEngine());
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

    private Collection<Usage> getUsages(IvyNode ivyNode, ReportConversionContext context) {
        LinkedHashSet<Usage> usages = new LinkedHashSet<Usage>();
        getUsages(ivyNode, context, usages, new HashSet<IvyNode>());
        return usages;
    }

    private void getUsages(IvyNode ivyNode, ReportConversionContext context, Collection<Usage> usages, Set<IvyNode> seen) {
        if (!seen.add(ivyNode)) {
            return;
        }

        // Reflect-o-rama to get at private ivy stuff

        // First, get the usages (ie incoming dependencies) of this node
        IvyNodeUsage usage = (IvyNodeUsage) getField(ivyNode, IvyNode.class, "usage");
        Map<String, Set<?>> dependersByRootConfig = (Map<String, Set<?>>) getField(usage, IvyNodeUsage.class, "dependers");
        Set<?> objects = dependersByRootConfig.get(context.conf);
        if (objects == null) {
            assert ivyNode.getRoot() == ivyNode;
            return;
        }
        for (Object object : objects) {
            DependencyDescriptor dependencyDescriptor = (DependencyDescriptor) getField(object, object.getClass(), "dd");
            String dependerConfig = (String) getField(object, object.getClass(), "dependerConf");
            usages.add(new Usage(dependerConfig, dependencyDescriptor));
        }

        // Need to collect up the usages from those nodes which were evicted by this node
        Map<ModuleRevisionId, IvyNodeUsage> mergedUsages = (Map<ModuleRevisionId, IvyNodeUsage>) getField(ivyNode, IvyNode.class, "mergedUsages");
        seen.add(ivyNode);
        for (IvyNodeUsage mergedUsage : mergedUsages.values()) {
            IvyNode mergedNode = (IvyNode) getField(mergedUsage, IvyNodeUsage.class, "node");
            // Need to filter out those usages where the parent (ie dependent) module was also evicted. These end up in the direct
            // usages of the node, above
            Collection<Usage> candidates = new LinkedHashSet<Usage>();
            getUsages(mergedNode, context, candidates, seen);
            for (Usage candidate : candidates) {
                ModuleRevisionId parentId = candidate.dependencyDescriptor.getParentRevisionId();
                IvyNode parentNode = context.configurationResolveReport.getDependency(parentId);
                // parent node will be null when the dependency belongs to the root module. Assume that the root has not been evicted
                assert parentNode != null || parentId.equals(context.configurationResolveReport.getModuleDescriptor().getModuleRevisionId());
                if (parentNode == null || isResolvedNode(parentNode, context.conf)) {
                    usages.add(candidate);
                }
            }
        }
    }

    private Object getField(Object object, Class<?> type, String field) {
        try {
            Field usagesField = type.getDeclaredField(field);
            usagesField.setAccessible(true);
            return usagesField.get(object);
        } catch (NoSuchFieldException e) {
            throw UncheckedException.asUncheckedException(e);
        } catch (IllegalAccessException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private Set<String> getRealConfigurations(IvyNode dependencyNode, Iterable<String> dependencyConfigurations) {
        Set<String> realDependencyConfigurations = new LinkedHashSet<String>();
        for (String dependencyConfiguration : dependencyConfigurations) {
            realDependencyConfigurations.addAll(WrapUtil.toSet(dependencyNode.getRealConfs(dependencyConfiguration)));
        }
        return realDependencyConfigurations;
    }

    private Set<ResolvedArtifact> getArtifacts(ResolvedDependency owner, IvyNode dependencyNode) {
        Set<ResolvedArtifact> resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
        Artifact[] artifacts = dependencyNode.getSelectedArtifacts(null);
        for (Artifact artifact : artifacts) {
            resolvedArtifacts.add(createResolvedArtifact(owner, artifact, dependencyNode));
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
            createFirstLevelDependenciesModuleRevisionIds(configuration.getAllDependencies().withType(ModuleDependency.class));
            conf = configuration.getName();
        }

        /**
         * Adds the given configuration, if not already.
         */
        public ConfigurationDetails addConfiguration(IvyNode ivyNode, String configuration) {
            ModuleRevisionId actualId = ivyNode.getResolvedId();
            Map<String, ConfigurationDetails> configurationsForNode = handledNodes.get(actualId);
            if (configurationsForNode == null) {
                configurationsForNode = new LinkedHashMap<String, ConfigurationDetails>();
                handledNodes.put(actualId, configurationsForNode);
            }
            ConfigurationDetails configurationDetails = configurationsForNode.get(configuration);
            if (configurationDetails != null) {
                return configurationDetails;
            }

            configurationDetails = createConfiguration(ivyNode, configuration);
            this.configurations.put(configurationDetails.dependency.getId(), configurationDetails);
            configurationsForNode.put(configuration, configurationDetails);

            // Collect top level dependencies
            ResolvedConfigurationIdentifier originalId = new ResolvedConfigurationIdentifier(ivyNode.getId(),
                    configuration);
            if (firstLevelDependenciesModuleRevisionIds.containsKey(originalId)) {
                ModuleDependency firstLevelNode = firstLevelDependenciesModuleRevisionIds.get(originalId);
                firstLevelResolvedDependencies.get(firstLevelNode).add(configurationDetails.dependency);
            }

            return configurationDetails;
        }
        
        public ConfigurationDetails createConfiguration(IvyNode ivyNode, String configuration) {
            ModuleRevisionId actualId = ivyNode.getResolvedId();
            Set<String> configurations = getConfigurationHierarchy(ivyNode, configuration);
            DefaultResolvedDependency resolvedDependency;
            if (actualId.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY) != null) {
                resolvedDependency = new DefaultResolvedDependency(
                        actualId.getAttribute(DependencyDescriptorFactory.PROJECT_PATH_KEY),
                        actualId.getOrganisation(), actualId.getName(), actualId.getRevision(),
                        configuration);
            } else {
                resolvedDependency = new DefaultResolvedDependency(
                        actualId.getOrganisation(), actualId.getName(), actualId.getRevision(),
                        configuration);
            }
            Set<ResolvedArtifact> moduleArtifacts = getArtifacts(resolvedDependency, ivyNode);
            for (ResolvedArtifact artifact : moduleArtifacts) {
                resolvedDependency.addModuleArtifact(artifact);
            }

            return new ConfigurationDetails(resolvedDependency, ivyNode, configurations);
        }

        private void createFirstLevelDependenciesModuleRevisionIds(Set<ModuleDependency> firstLevelDependencies) {
            for (ModuleDependency firstLevelDependency : firstLevelDependencies) {
                ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(dependencyDescriptorFactory.createModuleRevisionId(firstLevelDependency), firstLevelDependency.getConfiguration());
                firstLevelDependenciesModuleRevisionIds.put(id, firstLevelDependency);
                firstLevelResolvedDependencies.put(firstLevelDependency, new LinkedHashSet<ResolvedDependency>());
            }
        }

        public ConfigurationDetails getConfiguration(IvyNode node, String configName) {
            return getConfiguration(node.getResolvedId(), configName);
        }

        public ConfigurationDetails getConfiguration(ModuleRevisionId resolvedId, String configName) {
            Map<String, ConfigurationDetails> configurationsForNode = getModule(resolvedId);
            ConfigurationDetails configuration = configurationsForNode.get(configName);
            if (configuration == null) {
                throw new IllegalArgumentException(String.format("Unknown configuration '%s' for node %s.", configName, resolvedId));
            }
            return configuration;
        }

        private Map<String, ConfigurationDetails> getModule(ModuleRevisionId moduleId) {
            Map<String, ConfigurationDetails> configurationsForNode = handledNodes.get(moduleId);
            if (configurationsForNode == null) {
                throw new IllegalArgumentException(String.format("Unknown node %s.", moduleId));
            }
            return configurationsForNode;
        }

        public Set<ConfigurationDetails> findExtendingConfigurations(ModuleRevisionId moduleId, String configName) {
            Set<ConfigurationDetails> result = new LinkedHashSet<ConfigurationDetails>();
            Map<String, ConfigurationDetails> module = getModule(moduleId);
            for (ConfigurationDetails configurationDetails : module.values()) {
                if (configurationDetails.configurationHierarchy.contains(configName)) {
                    result.add(configurationDetails);
                }
            }
            return result;
        }
    }

    private static class Usage {
        private final DependencyDescriptor dependencyDescriptor;
        private final String dependerConfig;

        private Usage(String dependerConfig, DependencyDescriptor dependencyDescriptor) {
            this.dependerConfig = dependerConfig;
            this.dependencyDescriptor = dependencyDescriptor;
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

        @Override
        public String toString() {
            return dependency.toString();
        }
    }
}
