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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.util.WrapUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public abstract class AbstractDependencyDescriptorFactoryInternal implements DependencyDescriptorFactoryInternal {
    private ExcludeRuleConverter excludeRuleConverter;

    public AbstractDependencyDescriptorFactoryInternal(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public DefaultDependencyDescriptor addDependencyDescriptor(Configuration configuration, DefaultModuleDescriptor moduleDescriptor, ModuleDependency dependency) {
        DefaultDependencyDescriptor descriptor = addDependencyDescriptor(configuration.getName(), moduleDescriptor, dependency);
        workAroundIvyLimitationsByCopyingDefaultIncludesForExtendedDependencies(configuration, descriptor, dependency);
        return descriptor;
    }

    protected void workAroundIvyLimitationsByCopyingDefaultIncludesForExtendedDependencies(Configuration configuration,
                                                                                           DefaultDependencyDescriptor dependencyDescriptor,
                                                                                           ModuleDependency newDependency) {
        // Do nothing by default - only required for external dependencies
    }

    public DefaultDependencyDescriptor addDependencyDescriptor(String configuration, DefaultModuleDescriptor moduleDescriptor, ModuleDependency dependency) {
        ModuleRevisionId moduleRevisionId = createModuleRevisionId(dependency);
        DefaultDependencyDescriptor newDescriptor = createDependencyDescriptor(dependency, configuration, moduleDescriptor, moduleRevisionId);

        DefaultDependencyDescriptor matchingDependencyDescriptor = findMatchingDescriptorForSameConfiguration(moduleDescriptor, newDescriptor);
        if (matchingDependencyDescriptor != null) {
            mergeDescriptors(configuration, matchingDependencyDescriptor, newDescriptor, dependency);
            return matchingDependencyDescriptor;
        }

        moduleDescriptor.addDependency(newDescriptor);
        return newDescriptor;
    }

    protected abstract DefaultDependencyDescriptor createDependencyDescriptor(ModuleDependency dependency, String configuration,
                                                            ModuleDescriptor moduleDescriptor, ModuleRevisionId moduleRevisionId);

    private DefaultDependencyDescriptor findMatchingDescriptorForSameConfiguration(DefaultModuleDescriptor moduleDescriptor, DependencyDescriptor targetDescriptor) {
        for (DependencyDescriptor dependencyDescriptor : moduleDescriptor.getDependencies()) {
            if (dependencyDescriptor.getDependencyRevisionId().equals(targetDescriptor.getDependencyRevisionId())
                    && Arrays.equals(dependencyDescriptor.getModuleConfigurations(), targetDescriptor.getModuleConfigurations())) {
                return (DefaultDependencyDescriptor) dependencyDescriptor;
            }
        }
        return null;
    }

    private void mergeDescriptors(String masterConfiguration, DefaultDependencyDescriptor matchingDependencyDescriptor, DependencyDescriptor newDescriptor, ModuleDependency newDependency) {
        // TODO Merge transitive, excludeRules and force flags
        // Merge dependency configurations
        if (newDependency.getConfiguration() != null) {
            matchingDependencyDescriptor.addDependencyConfiguration(masterConfiguration, newDependency.getConfiguration());
        }

        // Copy across all defined artifacts
        for (DependencyArtifactDescriptor artifactDescriptor : newDescriptor.getAllDependencyArtifacts()) {
            matchingDependencyDescriptor.addDependencyArtifact(masterConfiguration, artifactDescriptor);
        }

        // Copy across inclusion of default artifacts
        if (newDescriptor.getIncludeRules(masterConfiguration).length != 0) {
            includeDefaultArtifacts(masterConfiguration, matchingDependencyDescriptor);
        }
    }

    protected void includeDefaultArtifacts(String configuration, DefaultDependencyDescriptor dependencyDescriptor) {
        // Only add the default include rule once
        if (dependencyDescriptor.getIncludeRules(configuration).length == 0) {
            // Add '*' include rule if if one configuration has no defined artifacts and the other has defined artifacts
            ArtifactId aid = new ArtifactId(new ModuleId(PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION),
                    PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION, PatternMatcher.ANY_EXPRESSION);
            IncludeRule includeRule = new DefaultIncludeRule(aid, ExactPatternMatcher.INSTANCE, null);
            dependencyDescriptor.addIncludeRule(configuration, includeRule);
        }
    }

    protected void addExcludesArtifactsAndDependencies(String configuration, ModuleDependency dependency,
                                                     DefaultDependencyDescriptor dependencyDescriptor) {
        addArtifacts(configuration, dependency.getArtifacts(), dependencyDescriptor);
        addExcludes(configuration, dependency.getExcludeRules(), dependencyDescriptor);
        addDependencyConfiguration(configuration, dependency, dependencyDescriptor);
    }

    private void addArtifacts(String configuration, Set<DependencyArtifact> artifacts,
                              DefaultDependencyDescriptor dependencyDescriptor) {
        for (DependencyArtifact artifact : artifacts) {
            DefaultDependencyArtifactDescriptor artifactDescriptor;
            try {
                artifactDescriptor = new DefaultDependencyArtifactDescriptor(dependencyDescriptor, artifact.getName(),
                        artifact.getType(),
                        artifact.getExtension() != null ? artifact.getExtension() : artifact.getType(),
                        artifact.getUrl() != null ? new URL(artifact.getUrl()) : null,
                        artifact.getClassifier() != null ? WrapUtil.toMap(Dependency.CLASSIFIER,
                                artifact.getClassifier()) : null);
            } catch (MalformedURLException e) {
                throw new InvalidUserDataException("URL for artifact can't be parsed: " + artifact.getUrl(), e);
            }
            dependencyDescriptor.addDependencyArtifact(configuration, artifactDescriptor);
        }

        if (dependencyDescriptor.getAllDependencyArtifacts().length == 0) {
            includeDefaultArtifacts(configuration, dependencyDescriptor);
        }
    }

    private void addDependencyConfiguration(String configuration, ModuleDependency dependency,
                                            DefaultDependencyDescriptor dependencyDescriptor) {
        dependencyDescriptor.addDependencyConfiguration(configuration, dependency.getConfiguration());
    }

    private void addExcludes(String configuration, Set<ExcludeRule> excludeRules,
                             DefaultDependencyDescriptor dependencyDescriptor) {
        for (ExcludeRule excludeRule : excludeRules) {
            dependencyDescriptor.addExcludeRule(configuration, excludeRuleConverter.createExcludeRule(configuration,
                    excludeRule));
        }
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }

    public void setExcludeRuleConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

}