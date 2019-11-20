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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.LockMode;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultDependencyLockingProvider implements DependencyLockingProvider {

    private static final Logger LOGGER = Logging.getLogger(DefaultDependencyLockingProvider.class);
    private static final DocumentationRegistry DOC_REG = new DocumentationRegistry();

    private static ComponentSelector toComponentSelector(ModuleComponentIdentifier lockIdentifier) {
        String lockedVersion = lockIdentifier.getVersion();
        VersionConstraint versionConstraint = DefaultMutableVersionConstraint.withVersion(lockedVersion);
        return DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockIdentifier.getGroup(), lockIdentifier.getModule()), versionConstraint);

    }

    private final DependencyLockingNotationConverter converter = new DependencyLockingNotationConverter();
    private final LockFileReaderWriter lockFileReaderWriter;
    private final boolean writeLocks;
    private final boolean partialUpdate;
    private final LockEntryFilter lockEntryFilter;
    private final DomainObjectContext context;
    private DependencySubstitutionRules dependencySubstitutionRules;
    private LockMode lockMode;
    private boolean used = false;

    public DefaultDependencyLockingProvider(FileResolver fileResolver, StartParameter startParameter, DomainObjectContext context, DependencySubstitutionRules dependencySubstitutionRules) {
        this.context = context;
        this.dependencySubstitutionRules = dependencySubstitutionRules;
        this.lockFileReaderWriter = new LockFileReaderWriter(fileResolver, context);
        this.writeLocks = startParameter.isWriteDependencyLocks();
        if (writeLocks) {
            LOGGER.debug("Write locks is enabled");
        }
        List<String> lockedDependenciesToUpdate = startParameter.getLockedDependenciesToUpdate();
        partialUpdate = !lockedDependenciesToUpdate.isEmpty();
        lockEntryFilter = LockEntryFilterFactory.forParameter(lockedDependenciesToUpdate);
        lockMode = LockMode.DEFAULT;
    }

    @Override
    public DependencyLockingState loadLockState(String configurationName) {
        recordUsage();
        if (!writeLocks || partialUpdate) {
            List<String> lockedModules = lockFileReaderWriter.readLockFile(configurationName);
            if (lockedModules == null && lockMode == LockMode.STRICT) {
                throw new MissingLockStateException(context.identityPath(configurationName).toString());
            }
            if (lockedModules != null) {
                Set<ModuleComponentIdentifier> results = Sets.newHashSetWithExpectedSize(lockedModules.size());
                for (String module : lockedModules) {
                    ModuleComponentIdentifier lockedIdentifier = parseLockNotation(configurationName, module);
                    if (!lockEntryFilter.isSatisfiedBy(lockedIdentifier) && !isSubstitutedInComposite(lockedIdentifier)) {
                        results.add(lockedIdentifier);
                    }
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded lock state for configuration '{}', state is: {}", context.identityPath(configurationName), lockedModules);
                } else {
                    LOGGER.info("Loaded lock state for configuration '{}'", context.identityPath(configurationName));
                }
                boolean strictlyValidate = !partialUpdate && lockMode != LockMode.LENIENT;
                return new DefaultDependencyLockingState(strictlyValidate, results);
            }
        }
        return DefaultDependencyLockingState.EMPTY_LOCK_CONSTRAINT;
    }

    private void recordUsage() {
        used = true;
    }

    private boolean hasRecordedUsage() {
        return used;
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
            throw new InvalidLockFileException(context.identityPath(configurationName).getPath(), e);
        }
        return lockedIdentifier;
    }

    @Override
    public void persistResolvedDependencies(String configurationName, Set<ModuleComponentIdentifier> resolvedModules, Set<ModuleComponentIdentifier> changingResolvedModules) {
        if (writeLocks) {
            List<String> modulesOrdered = getModulesOrdered(resolvedModules);
            lockFileReaderWriter.writeLockFile(configurationName, modulesOrdered);
            if (!changingResolvedModules.isEmpty()) {
                LOGGER.warn("Dependency lock state for configuration '{}' contains changing modules: {}. This means that dependencies content may still change over time. See {} for details.",
                    context.identityPath(configurationName), getModulesOrdered(changingResolvedModules), DOC_REG.getDocumentationFor("dependency_locking"));
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Persisted dependency lock state for configuration '{}', state is: {}", context.identityPath(configurationName), modulesOrdered);
            } else {
                LOGGER.lifecycle("Persisted dependency lock state for configuration '{}'", context.identityPath(configurationName));
            }
        }
    }

    private List<String> getModulesOrdered(Collection<ModuleComponentIdentifier> resolvedComponents) {
        List<String> modules = Lists.newArrayListWithCapacity(resolvedComponents.size());
        for (ModuleComponentIdentifier identifier : resolvedComponents) {
            modules.add(converter.convertToLockNotation(identifier));
        }
        Collections.sort(modules);
        return modules;
    }

    @Override
    public LockMode getLockMode() {
        return lockMode;
    }

    @Override
    public void setLockMode(LockMode mode) {
        if (hasRecordedUsage()) {
            throw new InvalidUserCodeException("It is illegal to modify the dependency locking mode after any dependency configuration has been resolved");
        }
        this.lockMode = mode;
    }

    private static class LockingDependencySubstitution implements DependencySubstitutionInternal {

        private ComponentSelector selector;
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
            return null;
        }

        @Override
        public boolean isUpdated() {
            return false;
        }
    }
}
