/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Thread safe description of what and how to resolve. This type is almost entirely deeply immutable,
 * except for actions which must run user code. Actions which run user code or actions that compute
 * state lazily must be thread safe. These actions are assumed to be effectively immutable and must
 * be deterministic. All user-code must be guarded with the proper project locks. Properties exposed
 * by these parameters should assume they will be interacted with without project locks being
 * acquired beforehand.
 * <p>
 * These parameters contain almost all information required to perform a resolution, except for the
 * data still present in {@link LegacyResolutionParameters}. The non-migrated
 * data do not yet satisfy the immutability and thread safety requirements of this type, and therefore
 * have not yet been migrated to this class.
 */
public class ResolutionParameters {

    private final ResolutionHost resolutionHost;
    private final LocalComponentGraphResolveState rootComponent;
    private final LocalVariantGraphResolveState rootVariant;
    private final ImmutableList<ModuleVersionLock> moduleVersionLocks;
    private final ResolutionStrategy.SortOrder defaultSortOrder;
    private final ConfigurationIdentity configurationIdentity;
    private final ImmutableArtifactTypeRegistry artifactTypeRegistry;
    private final ImmutableModuleReplacements moduleReplacements;
    private final ConflictResolution moduleConflictResolutionStrategy;
    private final String dependencyLockingId;
    private final boolean dependencyLockingEnabled;
    private final boolean includeAllSelectableVariantResults;
    private final boolean dependencyVerificationEnabled;
    private final boolean failingOnDynamicVersions;
    private final boolean failingOnChangingVersions;
    private final FailureResolutions failureResolutions;
    private final CacheExpirationControl cacheExpirationControl;

    public ResolutionParameters(
        ResolutionHost resolutionHost,
        LocalComponentGraphResolveState rootComponent,
        LocalVariantGraphResolveState rootVariant,
        ImmutableList<ModuleVersionLock> moduleVersionLocks,
        ResolutionStrategy.SortOrder defaultSortOrder,
        @Nullable ConfigurationIdentity configurationIdentity,
        ImmutableArtifactTypeRegistry artifactTypeRegistry,
        ImmutableModuleReplacements moduleReplacements,
        ConflictResolution moduleConflictResolutionStrategy,
        String dependencyLockingId,
        boolean dependencyLockingEnabled,
        boolean includeAllSelectableVariantResults,
        boolean dependencyVerificationEnabled,
        boolean failingOnDynamicVersions,
        boolean failingOnChangingVersions,
        FailureResolutions failureResolutions,
        CacheExpirationControl cacheExpirationControl
    ) {
        this.resolutionHost = resolutionHost;
        this.rootComponent = rootComponent;
        this.rootVariant = rootVariant;
        this.moduleVersionLocks = moduleVersionLocks;
        this.defaultSortOrder = defaultSortOrder;
        this.configurationIdentity = configurationIdentity;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.moduleReplacements = moduleReplacements;
        this.moduleConflictResolutionStrategy = moduleConflictResolutionStrategy;
        this.dependencyLockingId = dependencyLockingId;
        this.dependencyLockingEnabled = dependencyLockingEnabled;
        this.includeAllSelectableVariantResults = includeAllSelectableVariantResults;
        this.dependencyVerificationEnabled = dependencyVerificationEnabled;
        this.failingOnDynamicVersions = failingOnDynamicVersions;
        this.failingOnChangingVersions = failingOnChangingVersions;
        this.failureResolutions = failureResolutions;
        this.cacheExpirationControl = cacheExpirationControl;
    }

    /**
     * The "Host" or owner of the resolution.
     */
    public ResolutionHost getResolutionHost() {
        return resolutionHost;
    }

    /**
     * The root component of the graph. Owns the root variant.
     */
    public LocalComponentGraphResolveState getRootComponent() {
        return rootComponent;
    }

    /**
     * The root variant of the graph, specifying the declared dependencies and requested attributes.
     */
    public LocalVariantGraphResolveState getRootVariant() {
        return rootVariant;
    }

    /**
     * Version locks to apply to specific modules during resolution.
     * <p>
     * Enforces that the resolution will choose specific versions for the given modules,
     * to be used to enforce consistent versions across different resolutions.
     */
    public ImmutableList<ModuleVersionLock> getModuleVersionLocks() {
        return moduleVersionLocks;
    }

    public static class ModuleVersionLock  {

        private final ModuleIdentifier module;
        private final String version;
        private final String reason;
        private final boolean strict;

        public ModuleVersionLock(ModuleIdentifier module, String version, String consistentResolutionReason, boolean strict) {
            this.module = module;
            this.version = version;
            this.reason = consistentResolutionReason;
            this.strict = strict;
        }

        /**
         * The module to lock.
         */
        public ModuleIdentifier getModuleId() {
            return module;
        }

        /**
         * The version to enforce.
         */
        public String getVersion() {
            return version;
        }

        /**
         * Why this version is enforced.
         */
        public String getReason() {
            return reason;
        }

        /**
         * Whether the version be enforced as a strict constraint.
         */
        public boolean isStrict() {
            return strict;
        }

    }

    /**
     * The default sort ordering of artifacts. May be overridden during artifact selection.
     */
    public ResolutionStrategy.SortOrder getDefaultSortOrder() {
        return defaultSortOrder;
    }

    /**
     * The identity of the source of this resolution, if it is a Configuration.
     * <p>
     * Used by artifact transforms to identify the source configuration in build operations.
     * May be null if the resolution is not associated with a configuration.
     */
    public @Nullable ConfigurationIdentity getConfigurationIdentity() {
        return configurationIdentity;
    }

    /**
     * Specifies transformations applied to the attributes of producer artifact sets before
     * artifact selection is performed on them. These transformations are based on the
     * artifacts exposed the artifact sets being selected.
     * <p>
     * TODO #31538: This should go away. Observing the actual artifacts of an artifact set during
     * selection must be avoided, as we perform artifact selection during build dependency
     * resolution, which occurs before the actual artifacts are known.
     */
    public ImmutableArtifactTypeRegistry getArtifactTypeRegistry() {
        return artifactTypeRegistry;
    }

    /**
     * Specifies the module replacements to apply during resolution.
     */
    public ImmutableModuleReplacements getModuleReplacements() {
        return moduleReplacements;
    }

    /**
     * Specifies how module conflicts are resolved.
     */
    public ConflictResolution getModuleConflictResolutionStrategy() {
        return moduleConflictResolutionStrategy;
    }

    /**
     * Identifies this resolution within a lockfile.
     */
    public String getDependencyLockingId() {
        return dependencyLockingId;
    }

    /**
     * True if dependency locking is enabled, false otherwise.
     */
    public boolean isDependencyLockingEnabled() {
        return dependencyLockingEnabled;
    }

    /**
     * True if all selectable variants should be included in the output
     * {@link org.gradle.api.artifacts.result.ResolutionResult}. This should generally
     * be false except for reporting use cases.
     * <p>
     * TODO: The reporting use case of this parameter may be better suited by a
     * separate "metadata fetching" API, allowing component metadata to be retrieved
     * without performing graph resolution.
     */
    public boolean getIncludeAllSelectableVariantResults() {
        return includeAllSelectableVariantResults;
    }

    /**
     * True if dependency verification is enabled, false otherwise.
     */
    public boolean isDependencyVerificationEnabled() {
        return dependencyVerificationEnabled;
    }

    /**
     * True if resolution should fail if dependencies with dynamic versions are present in the graph, false otherwise.
     */
    public boolean isFailingOnDynamicVersions() {
        return failingOnDynamicVersions;
    }

    /**
     * True if resolution should fail if dependencies with changing versions are present in the graph, false otherwise.
     */
    public boolean isFailingOnChangingVersions() {
        return failingOnChangingVersions;
    }

    /**
     * Details about this resolution to provide additional context during failure cases.
     */
    public FailureResolutions getFailureResolutions() {
        return failureResolutions;
    }

    /**
     * Details about this resolution to provide additional context during failure cases.
     */
    public interface FailureResolutions {

        /**
         * Provide resolutions to add to a failure to assist the user on resolving the provided
         * version conflicts.
         */
        List<String> forVersionConflict(Set<Conflict> conflicts);
    }

    /**
     * Controls the caching behavior for external dependencies.
     */
    public CacheExpirationControl getCacheExpirationControl() {
        return cacheExpirationControl;
    }

}
