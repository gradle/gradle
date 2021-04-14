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
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Int2ObjectMapSerializer;
import org.gradle.internal.serialize.IntSetSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A data structure that holds constant to class mapping.
 * It consists of dependents class names `dependentClasses` and dependents `privateConstantDependents` and `accessibleConstantDependents` as `constant hash` => set(indexes) mapping.
 *
 * The idea here is that Strings take a lot of memory and there is one-to-many relationship between constant to dependents.
 * So that is why we store String names of dependents in a separate list and we access them via indexes.
 **/
public class ConstantToDependentsMapping {

    /**
     * Visited classes will not be serialized and accessible after reading from cache.
     * They are needed just as output of Compiler plugin to correctly merge new mapping to old one.
     */
    private transient final Set<String> visitedClasses;

    private final List<String> dependentClasses;
    private final Map<Integer, Set<Integer>> privateConstantDependents;
    private final Map<Integer, Set<Integer>> accessibleConstantDependents;

    ConstantToDependentsMapping(Set<String> visitedClasses,
                                        List<String> dependentClasses,
                                        Map<Integer, Set<Integer>> privateConstantDependents,
                                        Map<Integer, Set<Integer>> accessibleConstantDependents) {
        this.visitedClasses = visitedClasses;
        this.dependentClasses = dependentClasses;
        this.privateConstantDependents = privateConstantDependents;
        this.accessibleConstantDependents = accessibleConstantDependents;
    }

    public List<String> getDependentClasses() {
        return dependentClasses;
    }

    public Set<String> getVisitedClasses() {
        return visitedClasses;
    }

    public Map<Integer, Set<Integer>> getAccessibleConstantDependents() {
        return accessibleConstantDependents;
    }

    public Map<Integer, Set<Integer>> getPrivateConstantDependents() {
        return privateConstantDependents;
    }

    public Set<String> findPrivateConstantDependentsFor(int constantOriginHash) {
        return findDependentsFor(privateConstantDependents, constantOriginHash);
    }

    public Set<String> findAccessibleConstantDependentsFor(int constantOriginHash) {
        return findDependentsFor(accessibleConstantDependents, constantOriginHash);
    }

    private Set<String> findDependentsFor(Map<Integer, Set<Integer>> collection, int constantOriginHash) {
        if (collection.containsKey(constantOriginHash)) {
            Set<Integer> classIndexes = collection.get(constantOriginHash);
            Set<String> dependents = new ObjectOpenHashSet<>(classIndexes.size());
            classIndexes.forEach(index -> dependents.add(dependentClasses.get(index)));
            return dependents;
        }
        return Collections.emptySet();
    }

    public static ConstantToDependentsMapping empty() {
        return new ConstantToDependentsMapping(Collections.emptySet(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
    }

    public static ConstantToDependentsMappingBuilder builder() {
        return new ConstantToDependentsMappingBuilder();
    }

    public static final class Serializer extends AbstractSerializer<ConstantToDependentsMapping> {

        private final Int2ObjectMapSerializer<Set<Integer>> mapSerializer;
        private final ListSerializer<String> classNamesSerializer;

        public Serializer(StringInterner interner) {
            InterningStringSerializer stringSerializer = new InterningStringSerializer(interner);
            classNamesSerializer = new ListSerializer<>(stringSerializer);
            mapSerializer = new Int2ObjectMapSerializer<>(new JavaSetToIntSetSerializer());
        }

        @Override
        public ConstantToDependentsMapping read(Decoder decoder) throws Exception {
            List<String> classNames = classNamesSerializer.read(decoder);
            Map<Integer, Set<Integer>> privateDependentsIndexes = mapSerializer.read(decoder);
            Map<Integer, Set<Integer>> publicDependentsIndexes = mapSerializer.read(decoder);
            return new ConstantToDependentsMapping(Collections.emptySet(), classNames, privateDependentsIndexes, publicDependentsIndexes);
        }

        @Override
        public void write(Encoder encoder, ConstantToDependentsMapping value) throws Exception {
            classNamesSerializer.write(encoder, value.dependentClasses);
            mapSerializer.write(encoder, value.getPrivateConstantDependents());
            mapSerializer.write(encoder, value.getAccessibleConstantDependents());
        }
    }

    private static final class JavaSetToIntSetSerializer implements org.gradle.internal.serialize.Serializer<Set<Integer>> {
        @Override
        public Set<Integer> read(Decoder decoder) throws Exception {
            return IntSetSerializer.INSTANCE.read(decoder);
        }

        @Override
        public void write(Encoder encoder, Set<Integer> value) throws Exception {
            IntSetSerializer.INSTANCE.write(encoder, new IntOpenHashSet(value));
        }
    }

}
