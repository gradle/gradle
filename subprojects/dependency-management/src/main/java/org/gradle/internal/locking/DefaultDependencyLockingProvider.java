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

package org.gradle.internal.locking;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactSelectionDetails;
import org.gradle.api.artifacts.DependencyArtifactSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.LockMode;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.dsl.dependencies.LockEntryFilter;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ArtifactSelectionDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.resource.local.FileResourceListener;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.FeaturePreviews.Feature.ONE_LOCKFILE_PER_PROJECT;

public class DefaultDependencyLockingProvider implements DependencyLockingProvider {

    private static final Logger LOGGER = Logging.getLogger(DefaultDependencyLockingProvider.class);
    private static final DocumentationRegistry DOC_REG = new DocumentationRegistry();

    private final DependencyLockingNotationConverter converter = new DependencyLockingNotationConverter();
    private final LockFileReaderWriter lockFileReaderWriter;
    private final boolean writeLocks;
    private final boolean partialUpdate;
    private final LockEntryFilter updateLockEntryFilter;
    private final DomainObjectContext context;
    private final DependencySubstitutionRules dependencySubstitutionRules;
    private final boolean uniqueLockStateEnabled;
    private final Property<LockMode> lockMode;
    private final RegularFileProperty lockFile;
    private final ListProperty<String> ignoredDependencies;
    private boolean uniqueLockStateLoaded;
    private Map<String, List<String>> allLockState;
    private LockEntryFilter compoundLockEntryFilter;
    private LockEntryFilter ignoredEntryFilter;

    public DefaultDependencyLockingProvider(FileResolver fileResolver, StartParameter startParameter, DomainObjectContext context, DependencySubstitutionRules dependencySubstitutionRules, FeaturePreviews featurePreviews, PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory, FileResourceListener listener) {
        this.context = context;
        this.dependencySubstitutionRules = dependencySubstitutionRules;
        this.writeLocks = startParameter.isWriteDependencyLocks();
        if (writeLocks) {
            LOGGER.debug("Write locks is enabled");
        }
        List<String> lockedDependenciesToUpdate = startParameter.getLockedDependenciesToUpdate();
        partialUpdate = !lockedDependenciesToUpdate.isEmpty();
        updateLockEntryFilter = LockEntryFilterFactory.forParameter(lockedDependenciesToUpdate, "Update lock", true);
        uniqueLockStateEnabled = featurePreviews.isFeatureEnabled(ONE_LOCKFILE_PER_PROJECT);
        lockMode = propertyFactory.property(LockMode.class);
        lockMode.convention(LockMode.DEFAULT);
        lockFile = filePropertyFactory.newFileProperty();
        ignoredDependencies = propertyFactory.listProperty(String.class);
        this.lockFileReaderWriter = new LockFileReaderWriter(fileResolver, context, lockFile, listener);
    }

    private static ComponentSelector toComponentSelector(ModuleComponentIdentifier lockIdentifier) {
        String lockedVersion = lockIdentifier.getVersion();
        VersionConstraint versionConstraint = DefaultMutableVersionConstraint.withVersion(lockedVersion);
        return DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockIdentifier.getGroup(), lockIdentifier.getModule()), versionConstraint);
    }

    @Override
    public DependencyLockingState loadLockState(String configurationName) {
        recordUsage();
        if (uniqueLockStateEnabled) {
            loadLockState();
        }
        if (!writeLocks || partialUpdate) {
            List<String> lockedModules = findLockedModules(configurationName, uniqueLockStateEnabled);
            if (lockedModules == null && lockMode.get() == LockMode.STRICT) {
                throw new MissingLockStateException(context.identityPath(configurationName).toString());
            }
            if (lockedModules != null) {
                Set<ModuleComponentIdentifier> results = Sets.newHashSetWithExpectedSize(lockedModules.size());
                for (String module : lockedModules) {
                    ModuleComponentIdentifier lockedIdentifier = parseLockNotation(configurationName, module);
                    if (!getCompoundLockEntryFilter().isSatisfiedBy(lockedIdentifier) && !isSubstitutedInComposite(lockedIdentifier)) {
                        results.add(lockedIdentifier);
                    }
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded lock state for configuration '{}', state is: {}", context.identityPath(configurationName), lockedModules);
                } else {
                    LOGGER.info("Loaded lock state for configuration '{}'", context.identityPath(configurationName));
                }
                boolean strictlyValidate = !partialUpdate && lockMode.get() != LockMode.LENIENT;
                return new DefaultDependencyLockingState(strictlyValidate, results, getIgnoredEntryFilter());
            }
        }
        return DefaultDependencyLockingState.EMPTY_LOCK_CONSTRAINT;
    }

    private LockEntryFilter getCompoundLockEntryFilter() {
        if (compoundLockEntryFilter == null) {
            compoundLockEntryFilter = LockEntryFilterFactory.combine(getIgnoredEntryFilter(), updateLockEntryFilter);
        }
        return compoundLockEntryFilter;
    }

    private LockEntryFilter getIgnoredEntryFilter() {
        if (ignoredEntryFilter == null) {
            ignoredEntryFilter = LockEntryFilterFactory.forParameter(ignoredDependencies.getOrElse(Collections.emptyList()), "Ignored dependencies", false);
        }
        return ignoredEntryFilter;
    }

    @Nullable
    private List<String> findLockedModules(String configurationName, boolean uniqueLockStateEnabled) {
        List<String> result = null;
        if (uniqueLockStateEnabled) {
            result = allLockState.get(configurationName);
        }
        if (result == null) {
            result = lockFileReaderWriter.readLockFile(configurationName);
        }
        return result;
    }

    private synchronized void loadLockState() {
        if (!uniqueLockStateLoaded) {
            try {
                allLockState = lockFileReaderWriter.readUniqueLockFile();
                uniqueLockStateLoaded = true;
            } catch (IllegalStateException e) {
                throw new InvalidLockFileException("project '" + context.getProjectPath().getPath() + "'", e);
            }
        }
    }

    private void recordUsage() {
        lockMode.finalizeValue();
        lockFile.finalizeValue();
        ignoredDependencies.finalizeValue();
    }

    private boolean isSubstitutedInComposite(ModuleComponentIdentifier lockedIdentifier) {
        if (dependencySubstitutionRules.hasRules()) {
            LockingDependencySubstitution lockingDependencySubstitution = new LockingDependencySubstitution(toComponentSelector(lockedIdentifier));
            dependencySubstitutionRules.getRuleAction().execute(lockingDependencySubstitution);
            return lockingDependencySubstitution.didSubstitute();
        }
        return false;
    }

    private ModuleComponentIdentifier parseLockNotation(String configurationName, String module) {
        ModuleComponentIdentifier lockedIdentifier;
        try {
            lockedIdentifier = converter.convertFromLockNotation(module);
        } catch (IllegalArgumentException e) {
            throw new InvalidLockFileException("configuration '" + context.identityPath(configurationName).getPath() + "'", e);
        }
        return lockedIdentifier;
    }

    @Override
    public void persistResolvedDependencies(String configurationName, Set<ModuleComponentIdentifier> resolvedModules, Set<ModuleComponentIdentifier> changingResolvedModules) {
        if (writeLocks) {
            List<String> modulesOrdered = getModulesOrdered(resolvedModules);
            if (!changingResolvedModules.isEmpty()) {
                LOGGER.warn("Dependency lock state for configuration '{}' contains changing modules: {}. This means that dependencies content may still change over time. See {} for details.",
                    context.identityPath(configurationName), getModulesOrdered(changingResolvedModules), DOC_REG.getDocumentationFor("dependency_locking"));
            }
            if (uniqueLockStateEnabled) {
                allLockState.put(configurationName, modulesOrdered);
            } else {
                lockFileReaderWriter.writeLockFile(configurationName, modulesOrdered);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Persisted dependency lock state for configuration '{}', state is: {}", context.identityPath(configurationName), modulesOrdered);
                } else {
                    LOGGER.lifecycle("Persisted dependency lock state for configuration '{}'", context.identityPath(configurationName));
                }
            }
        }
    }

    @Override
    public void buildFinished() {
        if (uniqueLockStateEnabled && uniqueLockStateLoaded && lockFileReaderWriter.canWrite()) {
            lockFileReaderWriter.writeUniqueLockfile(allLockState);
            if (context.isScript()) {
                LOGGER.lifecycle("Persisted dependency lock state for buildscript of project '{}'", context.getProjectPath());
            } else {
                LOGGER.lifecycle("Persisted dependency lock state for project '{}'", context.getProjectPath());
            }
        }
    }

    private List<String> getModulesOrdered(Collection<ModuleComponentIdentifier> resolvedComponents) {
        List<String> modules = Lists.newArrayListWithCapacity(resolvedComponents.size());
        for (ModuleComponentIdentifier identifier : resolvedComponents) {
            if (!getIgnoredEntryFilter().isSatisfiedBy(identifier)) {
                modules.add(converter.convertToLockNotation(identifier));
            }
        }
        Collections.sort(modules);
        return modules;
    }

    @Override
    public Property<LockMode> getLockMode() {
        return lockMode;
    }

    @Override
    public RegularFileProperty getLockFile() {
        return lockFile;
    }

    @Override
    public ListProperty<String> getIgnoredDependencies() {
        return ignoredDependencies;
    }

    private static class LockingDependencySubstitution implements DependencySubstitutionInternal {

        private final ComponentSelector selector;
        private boolean didSubstitute = false;

        private LockingDependencySubstitution(ComponentSelector selector) {
            this.selector = selector;
        }

        @Override
        public ComponentSelector getRequested() {
            return selector;
        }

        @Override
        public void useTarget(Object notation) {
            didSubstitute = true;
        }

        @Override
        public void useTarget(Object notation, String reason) {
            didSubstitute = true;
        }

        @Override
        public void artifactSelection(Action<? super ArtifactSelectionDetails> action) {
            throw new UnsupportedOperationException();
        }

        boolean didSubstitute() {
            return didSubstitute;
        }

        @Override
        public void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor) {
            didSubstitute = true;
        }

        @Override
        public ComponentSelector getTarget() {
            return selector;
        }

        @Override
        public List<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
            return Collections.emptyList();
        }

        @Override
        public boolean isUpdated() {
            return false;
        }

        @Override
        public ArtifactSelectionDetailsInternal getArtifactSelectionDetails() {
            return new NoOpArtifactSelectionDetails();
        }

        private static class NoOpArtifactSelectionDetails implements ArtifactSelectionDetailsInternal {
            @Override
            public boolean isUpdated() {
                return false;
            }

            @Override
            public List<DependencyArtifactSelector> getTargetSelectors() {
                return Collections.emptyList();
            }

            @Override
            public boolean hasSelectors() {
                return false;
            }

            @Override
            public List<DependencyArtifactSelector> getRequestedSelectors() {
                return Collections.emptyList();
            }

            @Override
            public void withoutArtifactSelectors() {

            }

            @Override
            public void selectArtifact(String type, @Nullable String extension, @Nullable String classifier) {

            }

            @Override
            public void selectArtifact(DependencyArtifactSelector selector) {

            }
        }
    }
}
