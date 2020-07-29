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
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RootLocalComponentMetadata extends DefaultLocalComponentMetadata {
    private final DependencyLockingProvider dependencyLockingProvider;

    public RootLocalComponentMetadata(ModuleVersionIdentifier moduleVersionIdentifier, ComponentIdentifier componentIdentifier, String status, AttributesSchemaInternal schema, DependencyLockingProvider dependencyLockingProvider) {
        super(moduleVersionIdentifier, componentIdentifier, status, schema);
        this.dependencyLockingProvider = dependencyLockingProvider;
    }

    @Override
    public BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, List<String> consumptionAlternatives, boolean canBeResolved, ImmutableCapabilities capabilities) {
        assert hierarchy.contains(name);
        DefaultLocalConfigurationMetadata conf = new RootLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionAlternatives, canBeResolved, capabilities);
        addToConfigurations(name, conf);
        return conf;
    }

    class RootLocalConfigurationMetadata extends DefaultLocalConfigurationMetadata implements RootConfigurationMetadata {
        private boolean configurationLocked;
        private DependencyLockingState dependencyLockingState;

        RootLocalConfigurationMetadata(String name,
                                       String description,
                                       boolean visible,
                                       boolean transitive,
                                       Set<String> extendsFrom,
                                       ImmutableSet<String> hierarchy,
                                       ImmutableAttributes attributes,
                                       boolean canBeConsumed,
                                       List<String> consumptionAlternatives,
                                       boolean canBeResolved,
                                       ImmutableCapabilities capabilities) {
            super(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionAlternatives, canBeResolved, capabilities);
        }

        @Override
        public void enableLocking() {
            this.configurationLocked = true;
        }

        @Override
        void maybeAddGeneratedDependencies(ImmutableList.Builder<LocalOriginDependencyMetadata> result) {
            if (configurationLocked) {
                dependencyLockingState = dependencyLockingProvider.loadLockState(getName());
                boolean strict = dependencyLockingState.mustValidateLockState();
                for (ModuleComponentIdentifier lockedDependency : dependencyLockingState.getLockedDependencies()) {
                    String lockedVersion = lockedDependency.getVersion();
                    VersionConstraint versionConstraint = strict
                        ? DefaultMutableVersionConstraint.withStrictVersion(lockedVersion)
                        : DefaultMutableVersionConstraint.withVersion(lockedVersion);
                    ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockedDependency.getGroup(), lockedDependency.getModule()), versionConstraint);
                    result.add(new LocalComponentDependencyMetadata(getComponentId(), selector, getName(), getAttributes(),  ImmutableAttributes.EMPTY, null,
                            Collections.emptyList(),  Collections.emptyList(), false, false, false, true, false, true, getLockReason(strict, lockedVersion)));
                }
            }
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
