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

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.LockHandling;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.locking.LockOutOfDateException.createLockOutOfDateException;
import static org.gradle.internal.locking.LockOutOfDateException.createLockOutOfDateExceptionStrictMode;

public class DefaultDependencyLockingProvider implements DependencyLockingProvider {

    private static final Logger LOGGER = Logging.getLogger(DefaultDependencyLockingProvider.class);

    private final DependencyFactory dependencyFactory;
    private final LockFileReaderWriter lockFileReaderWriter;
    private final Map<String, Map<String, ModuleComponentIdentifier>> resolvedConfigurations = new ConcurrentHashMap<String, Map<String, ModuleComponentIdentifier>>();
    private final AtomicReference<LockHandling> lockHandling = new AtomicReference<LockHandling>(LockHandling.VALIDATE);
    private final AtomicReference<List<String>> upgradeModules = new AtomicReference<List<String>>(Collections.<String>emptyList());
    private final boolean strict = true;

    public DefaultDependencyLockingProvider(DependencyFactory dependencyFactory, FileOperations fileOperations) {
        this.dependencyFactory = dependencyFactory;
        this.lockFileReaderWriter = new LockFileReaderWriter(fileOperations);
    }

    @Override
    public Set<DependencyConstraint> getLockedDependencies(String configurationName) {
        Set<DependencyConstraint> results = Collections.emptySet();
        if (lockHandling.get() != LockHandling.UPDATE_ALL) {
            List<String> lockedModules = lockFileReaderWriter.readLockFile(configurationName);
            if (lockedModules != null) {
                results = new HashSet<DependencyConstraint>(lockedModules.size());
                for (String module : lockedModules) {
                    if (mustCheckModule(module.substring(0, module.lastIndexOf(':')))) {
                        DependencyConstraint dependencyConstraint = dependencyFactory.createDependencyConstraint(module);
                        dependencyConstraint.version(new Action<MutableVersionConstraint>() {
                            @Override
                            public void execute(MutableVersionConstraint mutableVersionConstraint) {
                                mutableVersionConstraint.strictly(mutableVersionConstraint.getPreferredVersion());
                            }
                        });
                        dependencyConstraint.because("dependency-locking in place");
                        results.add(dependencyConstraint);
                    }
                }
            }
        }
        return results;
    }

    @Override
    public void updateLockHandling(LockHandling lockHandling) {
        if (this.lockHandling.compareAndSet(LockHandling.VALIDATE, lockHandling)) {
            writePreviouslyResolvedConfigurations();
        }
    }

    @Override
    public void setUpgradeModules(List<String> strings) {
        upgradeModules.set(Collections.unmodifiableList(strings));
    }

    private synchronized void writePreviouslyResolvedConfigurations() {
        for (Map.Entry<String, Map<String, ModuleComponentIdentifier>> resolvedConfiguration : resolvedConfigurations.entrySet()) {
            lockFileReaderWriter.writeLockFile(resolvedConfiguration.getKey(), resolvedConfiguration.getValue());
        }
    }

    @Override
    public void validateLockAligned(String configurationName, Map<String, ModuleComponentIdentifier> modules) {
        LockValidationState state = LockValidationState.VALID;
        List<String> lockedDependencies = lockFileReaderWriter.readLockFile(configurationName);
        if (lockedDependencies == null) {
            state = LockValidationState.NO_LOCK;
        }
        List<String> errors = new ArrayList<String>();
        Map<String, ModuleComponentIdentifier> extraModules = new HashMap<String, ModuleComponentIdentifier>(modules);
        if (state != LockValidationState.NO_LOCK && lockHandling.get() != LockHandling.UPDATE_ALL) {
            if (modules.keySet().size() > lockedDependencies.size()) {
                state = LockValidationState.VALID_APPENDED;
            }
            for (String line : lockedDependencies) {
                String module = line.substring(0, line.lastIndexOf(':'));
                extraModules.remove(module);
                if (mustCheckModule(module)) {
                    ModuleComponentIdentifier identifier = modules.get(module);
                    if (identifier == null) {
                        errors.add("Lock file contained '" + line + "' but it is not part of the resolved modules");
                    } else if (!line.contains(identifier.getVersion())) {
                        errors.add("Lock file expected '" + line + "' but resolution result was '" + module + ":" + identifier.getVersion() + "'");
                    }
                }
            }
            if (!errors.isEmpty()) {
                state = LockValidationState.INVALID;
            }
        }
        processResult(state, errors, extraModules.values());
        configurationResolved(configurationName, modules, state);
    }

    private void processResult(LockValidationState state, List<String> errors, Collection<ModuleComponentIdentifier> extraModules) {
        switch(state) {
            case INVALID:
                throw createLockOutOfDateException(errors);
            case VALID_APPENDED:
                if (strict) {
                    throw createLockOutOfDateExceptionStrictMode(extraModules);
                } else {
                    StringBuilder builder = new StringBuilder("Dependency lock found new modules:\n");
                    for (ModuleComponentIdentifier extraModule : extraModules) {
                        builder.append("\t").append(extraModule.getGroup()).append(":").append(extraModule.getModule()).append(":").append(extraModule.getVersion()).append("\n");
                    }
                    builder.append("\tLock file has been updated with these entries.");
                    LOGGER.lifecycle(builder.toString());
                }
            case VALID:
            case NO_LOCK:
                // Nothing to do
                break;
        }
    }

    private boolean mustCheckModule(String module) {
        return !upgradeModules.get().contains(module);
    }

    private void configurationResolved(String configurationName, Map<String, ModuleComponentIdentifier> modules, LockValidationState validationResult) {
        if (lockHandling.get() != LockHandling.VALIDATE || validationResult == LockValidationState.VALID_APPENDED) {
            lockFileReaderWriter.writeLockFile(configurationName, modules);
        } else {
            resolvedConfigurations.put(configurationName, modules);
            if (lockHandling.get() != LockHandling.VALIDATE) {
                writePreviouslyResolvedConfigurations();
            }
        }
    }

    enum LockValidationState {
        VALID,
        INVALID,
        VALID_APPENDED,
        NO_LOCK
    }

}
