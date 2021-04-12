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
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Int2ObjectMapSerializer;
import org.gradle.internal.serialize.IntSetSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A data structure that holds constant to class mapping.
 * It consists of dependents class names `classNames` and constantToClassIndexes `constant hash` => set(indexes) mapping.
 *
 * The idea here is that Strings take a lot of memory and there is one-to-many relationship between constant to dependents.
 * So that is why we store String names of dependents in a separate list and we access them via indexes.
 **/
@NonNullApi
public class ConstantToClassMapping {

    private final List<String> classNames;
    private final Map<Integer, IntSet> privateConstantDependents;
    private final Map<Integer, IntSet> publicConstantDependents;

    private ConstantToClassMapping(List<String> classNames,
                                   Map<Integer, IntSet> privateConstantDependents,
                                   Map<Integer, IntSet> publicConstantDependents) {
        this.classNames = classNames;
        this.privateConstantDependents = privateConstantDependents;
        this.publicConstantDependents = publicConstantDependents;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public Map<Integer, IntSet> getPublicConstantDependents() {
        return publicConstantDependents;
    }

    public Map<Integer, IntSet> getPrivateConstantDependents() {
        return privateConstantDependents;
    }

    public Set<String> findPrivateConstantDependentsForClassHash(int constantOriginHash) {
        return findDependentsForClassHash(privateConstantDependents, constantOriginHash);
    }

    public Set<String> findPublicConstantDependentsForClassHash(int constantOriginHash) {
        return findDependentsForClassHash(publicConstantDependents, constantOriginHash);
    }

    public boolean containsAny(int constantOriginHash) {
        return publicConstantDependents.containsKey(constantOriginHash) || privateConstantDependents.containsKey(constantOriginHash);
    }

    private Set<String> findDependentsForClassHash(Map<Integer, IntSet> collection, int constantOriginHash) {
        if (collection.containsKey(constantOriginHash)) {
            IntSet classIndexes = collection.get(constantOriginHash);
            Set<String> dependents = new ObjectOpenHashSet<>(classIndexes.size());
            classIndexes.forEach(index -> dependents.add(classNames.get(index)));
            return dependents;
        }
        return Collections.emptySet();
    }

    public static ConstantToClassMapping empty() {
        return new ConstantToClassMapping(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
    }

    public static ConstantToClassMappingBuilder builder() {
        return new ConstantToClassMappingBuilder();
    }

    public static final class ConstantToClassMappingBuilder {
        private final List<String> classNames;
        private final Map<Integer, IntSet> privateDependentsIndexes;
        private final Map<Integer, IntSet> publicDependentsIndexes;
        private final Map<String, Integer> classNameToIndex;

        ConstantToClassMappingBuilder() {
            this.classNames = new ArrayList<>();
            this.classNameToIndex = new Object2IntOpenHashMap<>();
            this.privateDependentsIndexes = new Object2ObjectOpenHashMap<>();
            this.publicDependentsIndexes = new Object2ObjectOpenHashMap<>();
        }

        public ConstantToClassMappingBuilder addPrivateDependent(int constantOriginHash, String dependent) {
            IntSet publicDependents = publicDependentsIndexes.getOrDefault(constantOriginHash, IntSets.EMPTY_SET);
            IntSet privateDependents = privateDependentsIndexes.computeIfAbsent(constantOriginHash, k -> new IntOpenHashSet());
            int dependentIndex = classNameToIndex.getOrDefault(dependent, -1);
            if (dependentIndex < 0 || !publicDependents.contains(dependentIndex)) {
                addDependent(privateDependents, dependent);
            }
            return this;
        }

        public ConstantToClassMappingBuilder addPublicDependent(int constantOriginHash, String dependent) {
            IntSet publicDependents = publicDependentsIndexes.computeIfAbsent(constantOriginHash, k -> new IntOpenHashSet());
            IntSet privateDependents = privateDependentsIndexes.getOrDefault(constantOriginHash, IntSets.EMPTY_SET);
            int dependentIndex = addDependent(publicDependents, dependent);
            if (!privateDependents.isEmpty()) {
                privateDependents.remove(dependentIndex);
            }
            return this;
        }

        private int addDependent(IntSet dependents, String dependent) {
            int dependentIndex = classNameToIndex.computeIfAbsent(dependent, k -> {
                classNames.add(dependent);
                return classNames.size() - 1;
            });
            dependents.add(dependentIndex);
            return dependentIndex;
        }

        public ConstantToClassMapping build() {
            privateDependentsIndexes.values().removeIf(Set::isEmpty);
            publicDependentsIndexes.values().removeIf(Set::isEmpty);
            return new ConstantToClassMapping(this.classNames, this.privateDependentsIndexes, publicDependentsIndexes);
        }

    }

    public static final class Serializer extends AbstractSerializer<ConstantToClassMapping> {

        private final Int2ObjectMapSerializer<IntSet> mapSerializer;
        private final ListSerializer<String> classNamesSerializer;

        public Serializer(StringInterner interner) {
            InterningStringSerializer stringSerializer = new InterningStringSerializer(interner);
            classNamesSerializer = new ListSerializer<>(stringSerializer);
            mapSerializer = new Int2ObjectMapSerializer<>(IntSetSerializer.INSTANCE);
        }

        @Override
        public ConstantToClassMapping read(Decoder decoder) throws Exception {
            List<String> classNames = classNamesSerializer.read(decoder);
            Map<Integer, IntSet> privateDependentsIndexes = mapSerializer.read(decoder);
            Map<Integer, IntSet> publicDependentsIndexes = mapSerializer.read(decoder);
            return new ConstantToClassMapping(classNames, privateDependentsIndexes, publicDependentsIndexes);
        }

        @Override
        public void write(Encoder encoder, ConstantToClassMapping value) throws Exception {
            classNamesSerializer.write(encoder, value.classNames);
            mapSerializer.write(encoder, value.getPrivateConstantDependents());
            mapSerializer.write(encoder, value.getPublicConstantDependents());
        }
    }

}
