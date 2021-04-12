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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToClassMapping.ConstantToClassMappingBuilder;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Class used to merge new constants mapping from compiler api results with old results
 *
 * It first transforms old mappings to: Map[String, IntSet] where key is class name and value are all constants that are used in that class
 */
@NonNullApi
public class ConstantToClassMappingMerger {

    /**
     * Merges mapping from CompilerApi in format:
     *   Dependency -> collect of Constant origins and old ConstantToClassMapping
     *
     * To new ConstantToClassMapping in format (so it's reversed to compiler api output):
     *   Constant origin hash -> collection of dependencies
     */
    public ConstantToClassMapping merge(ConstantToClassMapping newMapping, @Nullable ConstantToClassMapping oldMapping, Set<String> removedClasses) {
        if (oldMapping == null) {
            oldMapping = ConstantToClassMapping.empty();
        }
        return updateClassToConstantsMapping(newMapping, oldMapping, removedClasses);
    }

    private ConstantToClassMapping updateClassToConstantsMapping(ConstantToClassMapping newMapping, ConstantToClassMapping oldMapping, Set<String> removedClasses) {
        ConstantToClassMappingBuilder builder = new ConstantToClassMappingBuilder();
        IntSet removedClassesHashes = new IntOpenHashSet(removedClasses.size());
        removedClasses.forEach(it -> removedClassesHashes.add(it.hashCode()));
        oldMapping.getPublicConstantDependents().keySet().stream()
            .filter(constantOriginHash -> !removedClassesHashes.contains((int) constantOriginHash) && !newMapping.containsAny(constantOriginHash))
            .forEach(constantOriginHash -> addPublicDependents(builder, constantOriginHash, oldMapping.findPublicConstantDependentsForClassHash(constantOriginHash)));

        oldMapping.getPrivateConstantDependents().keySet().stream()
            .filter(constantOriginHash -> !removedClassesHashes.contains((int) constantOriginHash) && !newMapping.containsAny(constantOriginHash))
            .forEach(constantOriginHash -> addPrivateDependents(builder, constantOriginHash, oldMapping.findPrivateConstantDependentsForClassHash(constantOriginHash)));

        newMapping.getPublicConstantDependents().keySet().stream()
            .filter(constantOriginHash -> !removedClassesHashes.contains((int) constantOriginHash))
            .forEach(constantOriginHash -> addPublicDependents(builder, constantOriginHash, newMapping.findPublicConstantDependentsForClassHash(constantOriginHash)));

        newMapping.getPrivateConstantDependents().keySet().stream()
            .filter(constantOriginHash -> !removedClassesHashes.contains((int) constantOriginHash))
            .forEach(constantOriginHash -> addPrivateDependents(builder, constantOriginHash, newMapping.findPrivateConstantDependentsForClassHash(constantOriginHash)));

        return builder.build();
    }

    private void addPublicDependents(ConstantToClassMappingBuilder builder, int constantOriginHash, Set<String> dependents) {
        dependents.forEach(dependent -> builder.addPublicDependent(constantOriginHash, dependent));
    }

    private void addPrivateDependents(ConstantToClassMappingBuilder builder, int constantOriginHash, Set<String> dependents) {
        dependents.forEach(dependent -> builder.addPrivateDependent(constantOriginHash, dependent));
    }

}
