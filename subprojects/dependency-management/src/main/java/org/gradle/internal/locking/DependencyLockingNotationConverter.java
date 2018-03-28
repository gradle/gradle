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

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;

class DependencyLockingNotationConverter {

    DependencyConstraint convertToDependencyConstraint(String module) {
        int groupNameSeparatorIndex = module.indexOf(':');
        int nameVersionSeparatorIndex = module.lastIndexOf(':');
        if (groupNameSeparatorIndex * nameVersionSeparatorIndex < 0) {
            throw new IllegalArgumentException("The module notation does not respect the lock file format of 'group:name:version' - received " + module);
        }
        DefaultDependencyConstraint constraint = DefaultDependencyConstraint.strictConstraint(module.substring(0, groupNameSeparatorIndex),
                                                                                            module.substring(groupNameSeparatorIndex + 1, nameVersionSeparatorIndex),
                                                                                            module.substring(nameVersionSeparatorIndex + 1));

        constraint.because("dependency was locked to version " + constraint.getVersion());

        return constraint;
    }

    String convertToLockNotation(ModuleComponentIdentifier id) {
        return id.getDisplayName();
    }
}
