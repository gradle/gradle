/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.lazy.Lazy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class RootLocalComponentMetadata extends DefaultLocalComponentMetadata {
    private final DependencyLockingProvider dependencyLockingProvider;
    private final Map<String, RootLocalConfigurationMetadata> rootConfigs = Maps.newHashMap();

    public RootLocalComponentMetadata(ModuleVersionIdentifier moduleVersionIdentifier, ComponentIdentifier componentIdentifier, String status, AttributesSchemaInternal schema, DependencyLockingProvider dependencyLockingProvider) {
        super(moduleVersionIdentifier, componentIdentifier, status, schema);
        this.dependencyLockingProvider = dependencyLockingProvider;
    }

    @Override
    public BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, DeprecationMessageBuilder.WithDocumentation consumptionDeprecation, boolean canBeResolved, ImmutableCapabilities capabilities, Supplier<List<DependencyConstraint>> consistentResolutionConstraints) {
        assert hierarchy.contains(name);
        RootLocalConfigurationMetadata conf = new RootLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionDeprecation, canBeResolved, capabilities, consistentResolutionConstraints);
        addToConfigurations(name, conf);
        rootConfigs.put(name, conf);
        return conf;
    }

    /**
     * Returns the synthetic dependencies for the root configuration with the supplied name.
     * Synthetic dependencies are dependencies which are an internal implementation detail of Gradle,
     * used for example in dependency locking or consistent resolution. They are not "real" dependencies
     * in the sense that they are not added by users, and they are not always used during resolution
     * based on which phase of execution we are (task dependencies, execution, ...)
     * @param configuration the name of the configuration for which to get the synthetic dependencies
     * @return the synthetic dependencies of the requested configuration
     */
    public List<? extends DependencyMetadata> getSyntheticDependencies(String configuration) {
        return rootConfigs.get(configuration).getSyntheticDependencies();
    }

    class RootLocalConfigurationMetadata extends DefaultLocalConfigurationMetadata implements RootConfigurationMetadata {
        private final Supplier<List<DependencyConstraint>> consistentResolutionConstraints;
        private boolean configurationLocked;
        private DependencyLockingState dependencyLockingState;
        private final Lazy<List<LocalOriginDependencyMetadata>> syntheticDependencies = Lazy.locking().of(this::computeSyntheticDependencies);

        RootLocalConfigurationMetadata(String name,
                                       String description,
                                       boolean visible,
                                       boolean transitive,
                                       Set<String> extendsFrom,
                                       ImmutableSet<String> hierarchy,
                                       ImmutableAttributes attributes,
                                       boolean canBeConsumed,
                                       DeprecationMessageBuilder.WithDocumentation consumptionDeprecation,
                                       boolean canBeResolved,
                                       ImmutableCapabilities capabilities,
                                       Supplier<List<DependencyConstraint>> consistentResolutionConstraints) {
            super(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionDeprecation, canBeResolved, capabilities);
            this.consistentResolutionConstraints = consistentResolutionConstraints;
        }

        @Override
        public void enableLocking() {
            this.configurationLocked = true;
        }

        @Override
        List<LocalOriginDependencyMetadata> getSyntheticDependencies() {
            return syntheticDependencies.get();
        }

        private ImmutableList<LocalOriginDependencyMetadata> computeSyntheticDependencies() {
            ImmutableList.Builder<LocalOriginDependencyMetadata> syntheticDependencies = ImmutableList.builder();
            if (configurationLocked) {
                dependencyLockingState = dependencyLockingProvider.loadLockState(getName());
                boolean strict = dependencyLockingState.mustValidateLockState();
                for (ModuleComponentIdentifier lockedDependency : dependencyLockingState.getLockedDependencies()) {
                    String lockedVersion = lockedDependency.getVersion();
                    VersionConstraint versionConstraint = strict
                        ? DefaultMutableVersionConstraint.withStrictVersion(lockedVersion)
                        : DefaultMutableVersionConstraint.withVersion(lockedVersion);
                    ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockedDependency.getGroup(), lockedDependency.getModule()), versionConstraint);
                    syntheticDependencies.add(new LocalComponentDependencyMetadata(getComponentId(), selector, getName(), getAttributes(),  ImmutableAttributes.EMPTY, null,
                            Collections.emptyList(),  Collections.emptyList(), false, false, false, true, false, true, getLockReason(strict, lockedVersion)));
                }
            }
            List<DependencyConstraint> dependencyConstraints = consistentResolutionConstraints.get();
            for (DependencyConstraint dc : dependencyConstraints) {
                ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dc.getGroup(), dc.getName()), dc.getVersionConstraint());
                syntheticDependencies.add(new LocalComponentDependencyMetadata(getComponentId(), selector, getName(), getAttributes(),  ImmutableAttributes.EMPTY, null,
                    Collections.emptyList(),  Collections.emptyList(), false, false, false, true, false, true, dc.getReason()));
            }
            return syntheticDependencies.build();
        }

        private String getLockReason(boolean strict, String lockedVersion) {
            if (strict) {
                return "dependency was locked to version '" + lockedVersion + "'";
            }
            return "dependency was locked to version '" + lockedVersion + "' (update/lenient mode)";
        }

        @Override
        public DependencyLockingState getDependencyLockingState() {
            return dependencyLockingState;
        }
    }
}
