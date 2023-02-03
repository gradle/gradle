/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants;

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Class used to merge new constants mapping from compiler api results with old results
 */
public class ConstantToDependentsMappingMerger {

    public ConstantToDependentsMapping merge(ConstantToDependentsMapping newMapping, @Nullable ConstantToDependentsMapping oldMapping, Set<String> changedClasses) {
        if (oldMapping == null) {
            oldMapping = ConstantToDependentsMapping.empty();
        }
        return updateClassToConstantsMapping(newMapping, oldMapping, changedClasses);
    }

    private ConstantToDependentsMapping updateClassToConstantsMapping(ConstantToDependentsMapping newMapping, ConstantToDependentsMapping oldMapping, Set<String> changedClasses) {
        ConstantToDependentsMappingBuilder builder = ConstantToDependentsMapping.builder();
        oldMapping.getConstantDependents().keySet().stream()
            .filter(constantOrigin -> !changedClasses.contains(constantOrigin))
            .forEach(constantOrigin -> {
                DependentsSet dependents = oldMapping.getConstantDependentsForClass(constantOrigin);
                Set<String> accessibleDependents = new HashSet<>(dependents.getAccessibleDependentClasses());
                accessibleDependents.removeIf(changedClasses::contains);
                builder.addAccessibleDependents(constantOrigin, accessibleDependents);
                Set<String> privateDependents = new HashSet<>(dependents.getPrivateDependentClasses());
                privateDependents.removeIf(changedClasses::contains);
                builder.addPrivateDependents(constantOrigin, privateDependents);
            });
        newMapping.getConstantDependents().keySet()
            .forEach(constantOrigin -> {
                DependentsSet dependents = newMapping.getConstantDependentsForClass(constantOrigin);
                builder.addAccessibleDependents(constantOrigin, dependents.getAccessibleDependentClasses());
                builder.addPrivateDependents(constantOrigin, dependents.getPrivateDependentClasses());
            });
        return builder.build();
    }

}
