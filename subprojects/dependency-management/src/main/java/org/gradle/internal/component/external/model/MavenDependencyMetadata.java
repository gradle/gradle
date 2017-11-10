/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationNotFoundException;
import org.gradle.internal.component.model.DefaultDependencyMetadata;
import org.gradle.internal.component.model.Exclude;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MavenDependencyMetadata extends DefaultDependencyMetadata {
    private final MavenScope scope;
    private final Set<String> moduleConfigurations;
    private final List<Exclude> excludes;

    public MavenDependencyMetadata(MavenScope scope, boolean optional, ModuleComponentSelector selector, List<Artifact> artifacts, List<Exclude> excludes) {
        super(selector, artifacts, optional);
        this.scope = scope;
        if (isOptional() && scope != MavenScope.Test && scope != MavenScope.System) {
            moduleConfigurations = ImmutableSet.of("optional", scope.name().toLowerCase());
        } else {
            moduleConfigurations = ImmutableSet.of(scope.name().toLowerCase());
        }
        this.excludes = ImmutableList.copyOf(excludes);
    }

    @Override
    public String toString() {
        return "dependency: " + getSelector() + ", scope: " + scope + ", optional: " + isOptional();
    }

    public MavenScope getScope() {
        return scope;
    }

    @Override
    public Set<String> getModuleConfigurations() {
        return moduleConfigurations;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public boolean isTransitive() {
        return true;
    }

    @Override
    public boolean isForce() {
        return false;
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        if (!targetComponent.getVariantsForGraphTraversal().isEmpty()) {
            // This condition shouldn't be here, and attribute matching should always be applied when the target has variants
            // however, the schemas and metadata implementations are not yet set up for this, so skip this unless:
            // - the consumer has asked for something specific (by providing attributes), as the other metadata types are broken for the 'use defaults' case
            // - or the target is a component from a Maven repo as we can assume this is well behaved
            if (!consumerAttributes.isEmpty() || targetComponent instanceof MavenModuleResolveMetadata) {
                return ImmutableSet.of(selectConfigurationUsingAttributeMatching(consumerAttributes, targetComponent, consumerSchema));
            }
        }
        Set<ConfigurationMetadata> result = Sets.newLinkedHashSet();
        boolean requiresCompile = fromConfiguration.getName().equals("compile");
        if (!requiresCompile) {
            // From every configuration other than compile, include both the runtime and compile dependencies
            ConfigurationMetadata runtime = findTargetConfiguration(fromComponent, fromConfiguration, targetComponent, "runtime");
            result.add(runtime);
            requiresCompile = !runtime.getHierarchy().contains("compile");
        }
        if (requiresCompile) {
            // From compile configuration, or when the target's runtime configuration does not extend from compile, include the compile dependencies
            result.add(findTargetConfiguration(fromComponent, fromConfiguration, targetComponent, "compile"));
        }
        ConfigurationMetadata master = targetComponent.getConfiguration("master");
        if (master != null && (!master.getDependencies().isEmpty() || !master.getArtifacts().isEmpty())) {
            result.add(master);
        }
        return result;
    }

    private ConfigurationMetadata findTargetConfiguration(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, String target) {
        ConfigurationMetadata configuration = targetComponent.getConfiguration(target);
        if (configuration == null) {
            configuration = targetComponent.getConfiguration("default");
            if (configuration == null) {
                throw new ConfigurationNotFoundException(fromComponent.getComponentId(), fromConfiguration.getName(), target, targetComponent.getComponentId());
            }
        }
        return configuration;
    }

    @Override
    protected ModuleDependencyMetadata withRequested(ModuleComponentSelector newRequested) {
        return new MavenDependencyMetadata(scope, isOptional(), newRequested, getDependencyArtifacts(), getExcludes());
    }

    public List<Exclude> getExcludes() {
        return excludes;
    }

    @Override
    public List<Exclude> getExcludes(Collection<String> configurations) {
        return excludes;
    }

    @Override
    public String getDynamicConstraintVersion() {
        return getSelector().getVersionConstraint().getPreferredVersion();
    }

}
