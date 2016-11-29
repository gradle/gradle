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
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationNotFoundException;
import org.gradle.internal.component.model.DefaultDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;

import java.util.List;
import java.util.Set;

public class MavenDependencyMetadata extends DefaultDependencyMetadata {
    private final MavenScope scope;
    private final boolean optional;
    private final Set<String> moduleConfigurations;
    private final List<Exclude> excludes;
    private final ModuleExclusion exclusions;

    public MavenDependencyMetadata(MavenScope scope, boolean optional, ModuleVersionSelector requested, List<Artifact> artifacts, List<Exclude> excludes) {
        super(requested, artifacts);
        this.scope = scope;
        this.optional = optional;
        if (optional && scope != MavenScope.Test && scope != MavenScope.System) {
            moduleConfigurations = ImmutableSet.of("optional");
        } else {
            moduleConfigurations = ImmutableSet.of(scope.name().toLowerCase());
        }
        this.excludes = ImmutableList.copyOf(excludes);
        this.exclusions = ModuleExclusions.excludeAny(excludes);
    }

    @Override
    public String toString() {
        return "dependency: " + getRequested() + ", scope: " + scope + ", optional: " + optional;
    }

    public MavenScope getScope() {
        return scope;
    }

    @Override
    public Set<String> getModuleConfigurations() {
        return moduleConfigurations;
    }

    public boolean isOptional() {
        return optional;
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
    public String getDynamicConstraintVersion() {
        return getRequested().getVersion();
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchema attributesSchema) {
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
    protected DependencyMetadata withRequested(ModuleVersionSelector newRequested) {
        return new MavenDependencyMetadata(scope, optional, newRequested, getDependencyArtifacts(), getDependencyExcludes());
    }

    @Override
    public ModuleExclusion getExclusions(ConfigurationMetadata fromConfiguration) {
        return exclusions;
    }

    public List<Exclude> getDependencyExcludes() {
        return excludes;
    }
}
