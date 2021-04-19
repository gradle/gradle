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

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Class used to merge new constants mapping from compiler api results with old results
 */
public class ConstantToDependentsMappingMerger {

    public ConstantToDependentsMapping merge(ConstantToDependentsMapping newMapping, @Nullable ConstantToDependentsMapping oldMapping, Set<String> removedClasses) {
        if (oldMapping == null) {
            oldMapping = ConstantToDependentsMapping.empty();
        }
        return updateClassToConstantsMapping(newMapping, oldMapping, removedClasses);
    }

    private ConstantToDependentsMapping updateClassToConstantsMapping(ConstantToDependentsMapping newMapping, ConstantToDependentsMapping oldMapping, Set<String> removedClasses) {
        ConstantToDependentsMappingBuilder builder = ConstantToDependentsMapping.builder();
        Set<String> visitedClasses = newMapping.getVisitedClasses();
        oldMapping.getAccessibleConstantDependents().keySet().stream()
            .filter(constantOrigin -> !removedClasses.contains(constantOrigin))
            .forEach(constantOrigin -> {
                Set<String> constantDependents = oldMapping.findAccessibleConstantDependentsFor(constantOrigin);
                constantDependents.removeIf(wasDependentVisitedOrRemoved(removedClasses, visitedClasses));
                builder.addAccessibleDependents(constantOrigin, constantDependents);
            });

        oldMapping.getPrivateConstantDependents().keySet().stream()
            .filter(constantOrigin -> !removedClasses.contains(constantOrigin))
            .forEach(constantOriginHash -> {
                Set<String> constantDependents = oldMapping.findPrivateConstantDependentsFor(constantOriginHash);
                constantDependents.removeIf(wasDependentVisitedOrRemoved(removedClasses, visitedClasses));
                builder.addPrivateDependents(constantOriginHash, constantDependents);
            });

        newMapping.getAccessibleConstantDependents().keySet().stream()
            .filter(constantOrigin -> !removedClasses.contains(constantOrigin))
            .forEach(constantOriginHash -> {
                Set<String> constantDependents = newMapping.findAccessibleConstantDependentsFor(constantOriginHash);
                constantDependents.removeIf(wasDependentRemoved(removedClasses));
                builder.addAccessibleDependents(constantOriginHash, constantDependents);
            });

        newMapping.getPrivateConstantDependents().keySet().stream()
            .filter(constantOrigin -> !removedClasses.contains(constantOrigin))
            .forEach(constantOrigin -> {
                Set<String> constantDependents = newMapping.findPrivateConstantDependentsFor(constantOrigin);
                constantDependents.removeIf(wasDependentRemoved(removedClasses));
                builder.addPrivateDependents(constantOrigin, constantDependents);
            });

        return builder.build();
    }

    private Predicate<String> wasDependentVisitedOrRemoved(Set<String> removedClasses, Set<String> visitedClasses) {
        return dependent -> visitedClasses.contains(dependent) || removedClasses.contains(dependent);
    }

    private Predicate<String> wasDependentRemoved(Set<String> removedClasses) {
        return removedClasses::contains;
    }

}
