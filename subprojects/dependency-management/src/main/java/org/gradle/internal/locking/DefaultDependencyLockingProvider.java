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
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultDependencyLockingProvider implements DependencyLockingProvider {

    private static final Logger LOGGER = Logging.getLogger(DefaultDependencyLockingProvider.class);

    private final DependencyLockingNotationConverter converter;
    private final LockFileReaderWriter lockFileReaderWriter;
    private final boolean writeLocks;
    private final boolean partialUpdate;
    private final LockEntryFilter lockEntryFilter;

    public DefaultDependencyLockingProvider(FileResolver fileResolver, StartParameter startParameter) {
        this.lockFileReaderWriter = new LockFileReaderWriter(fileResolver);
        this.writeLocks = startParameter.isWriteDependencyLocks();
        if (writeLocks) {
            LOGGER.debug("Write locks is enabled");
        }
        List<String> lockedDependenciesToUpdate = startParameter.getLockedDependenciesToUpdate();
        this.partialUpdate = !lockedDependenciesToUpdate.isEmpty();
        lockEntryFilter = LockEntryFilterFactory.forParameter(lockedDependenciesToUpdate);
        converter = new DependencyLockingNotationConverter(!lockedDependenciesToUpdate.isEmpty());
    }

    @Override
    public DependencyLockingState loadLockState(String configurationName) {
        if (!writeLocks || partialUpdate) {
            List<String> lockedModules = lockFileReaderWriter.readLockFile(configurationName);
            if (lockedModules != null) {
                Set<DependencyConstraint> results = Sets.newHashSetWithExpectedSize(lockedModules.size());
                for (String module : lockedModules) {
                    if (lockEntryFilter.filters(module)) {
                        continue;
                    }
                    try {
                        results.add(converter.convertToDependencyConstraint(module));
                    } catch (IllegalArgumentException e) {
                        throw new InvalidLockFileException(configurationName, e);
                    }
                }
                LOGGER.debug("Found for configuration '{}' locking constraints: {}", configurationName, lockedModules);
                return new DefaultDependencyLockingState(partialUpdate, results);
            }
        }
        return DefaultDependencyLockingState.EMPTY_LOCK_CONSTRAINT;
    }

    @Override
    public void persistResolvedDependencies(String configurationName, Set<ModuleComponentIdentifier> resolvedModules) {
        if (writeLocks) {
            lockFileReaderWriter.writeLockFile(configurationName, getModulesOrdered(resolvedModules));
        }
    }

    private List<String> getModulesOrdered(Collection<ModuleComponentIdentifier> resolvedComponents) {
        List<String> modules = Lists.newArrayListWithCapacity(resolvedComponents.size());
        for (ModuleComponentIdentifier identifier : resolvedComponents) {
            modules.add(converter.convertToLockNotation(identifier));
        }
        Collections.sort(modules);
        LOGGER.debug("Found the following modules:\n\t{}", modules);
        return modules;
    }

}
