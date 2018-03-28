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
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultDependencyLockingProvider implements DependencyLockingProvider {

    private static final Logger LOGGER = Logging.getLogger(DefaultDependencyLockingProvider.class);

    private final DependencyLockingNotationConverter converter = new DependencyLockingNotationConverter();
    private final LockFileReaderWriter lockFileReaderWriter;
    private final boolean writeLocks;

    public DefaultDependencyLockingProvider(FileResolver fileResolver, StartParameter startParameter) {
        this.lockFileReaderWriter = new LockFileReaderWriter(fileResolver);
        this.writeLocks = startParameter.isWriteDependencyLocks();
    }

    @Override
    public Set<DependencyConstraint> findLockedDependencies(String configurationName) {
        Set<DependencyConstraint> results = Collections.emptySet();
        if (!writeLocks) {
            List<String> lockedModules = lockFileReaderWriter.readLockFile(configurationName);
            if (lockedModules != null) {
                results = Sets.newHashSetWithExpectedSize(lockedModules.size());
                for (String module : lockedModules) {
                    try {
                        results.add(converter.convertToDependencyConstraint(module));
                    } catch (IllegalArgumentException e) {
                        throw new InvalidLockFileException(configurationName, e);
                    }
                }
            }
        }
        return results;
    }

    @Override
    public void persistResolvedDependencies(String configurationName, Set<ResolvedComponentResult> resolvedComponents) {
        if (writeLocks) {
            lockFileReaderWriter.writeLockFile(configurationName, getModulesOrdered(resolvedComponents));
        }
    }

    private List<String> getModulesOrdered(Set<ResolvedComponentResult> resolvedComponents) {
        List<String> modules = Lists.newArrayListWithCapacity(resolvedComponents.size());
        for (ResolvedComponentResult resolvedComponentResult : resolvedComponents) {
            // TODO this filtering needs to happen at the caller instead
            if (resolvedComponentResult.getId() instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier id = (ModuleComponentIdentifier) resolvedComponentResult.getId();
                modules.add(converter.convertToLockNotation(id));
            }
        }
        Collections.sort(modules);
        LOGGER.warn("Found the following modules:\n\t{}", modules);
        return modules;
    }
}
