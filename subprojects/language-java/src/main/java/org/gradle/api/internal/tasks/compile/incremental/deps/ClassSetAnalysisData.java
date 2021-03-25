/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.IntSetSerializer;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassSetAnalysisData {

    public static ClassSetAnalysisData merge(List<ClassSetAnalysisData> datas) {
        int classCount = 0;
        int constantsCount = 0;
        int dependentsCount = 0;
        for (ClassSetAnalysisData data : datas) {
            classCount += data.classHashes.size();
            constantsCount += data.classesToConstants.size();
            dependentsCount += data.dependents.size();
        }

        Map<String, HashCode> classHashes = new HashMap<>(classCount);
        Map<String, IntSet> classesToConstants = new HashMap<>(constantsCount);
        Multimap<String, DependentsSet> dependents = ArrayListMultimap.create(dependentsCount, 10);
        String fullRebuildCause = null;

        for (ClassSetAnalysisData data : Lists.reverse(datas)) {
            classHashes.putAll(data.classHashes);
            classesToConstants.putAll(data.classesToConstants);
            data.dependents.forEach(dependents::put);
            if (fullRebuildCause == null) {
                fullRebuildCause = data.fullRebuildCause;
            }
        }
        ImmutableMap.Builder<String, DependentsSet> mergedDependents = ImmutableMap.builderWithExpectedSize(dependents.size());
        for (Map.Entry<String, Collection<DependentsSet>> entry : dependents.asMap().entrySet()) {
            mergedDependents.put(entry.getKey(), DependentsSet.merge(entry.getValue()));
        }
        return new ClassSetAnalysisData(classHashes, mergedDependents.build(), classesToConstants, fullRebuildCause);
    }

    private final Map<String, HashCode> classHashes;
    private final Map<String, DependentsSet> dependents;
    private final Map<String, IntSet> classesToConstants;
    private final String fullRebuildCause;

    public ClassSetAnalysisData() {
        this(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), null);
    }

    public ClassSetAnalysisData(Map<String, HashCode> classHashes, Map<String, DependentsSet> dependents, Map<String, IntSet> classesToConstants, String fullRebuildCause) {
        this.classHashes = classHashes;
        this.dependents = dependents;
        this.classesToConstants = classesToConstants;
        this.fullRebuildCause = fullRebuildCause;
    }

    public ClassSetAnalysisData reduceToTypesAffecting(ClassSetAnalysisData other) {
        if (fullRebuildCause != null) {
            return this;
        }
        Set<String> usedClasses = new HashSet<>(classHashes.size());
        for (Map.Entry<String, DependentsSet> entry : dependents.entrySet()) {
            if (entry.getValue().isDependencyToAll()) {
                usedClasses.add(entry.getKey());
            }
        }
        usedClasses.addAll(classesToConstants.keySet());

        Multimap<String, String> reverseDependencies = ArrayListMultimap.create(dependents.size(), 10);
        for (Map.Entry<String, DependentsSet> entry : dependents.entrySet()) {
            if (entry.getValue().isDependencyToAll()) {
                continue;
            }
            for (String dependent : entry.getValue().getAccessibleDependentClasses()) {
                reverseDependencies.put(dependent, entry.getKey());
            }
        }

        Set<String> newlyAdded = other.dependents.keySet();
        while (usedClasses.addAll(newlyAdded)) {
            HashSet<String> transitives = new HashSet<>(newlyAdded.size());
            for (String newly : newlyAdded) {
                transitives.addAll(reverseDependencies.get(newly));
            }
            newlyAdded = transitives;
        }

        Map<String, HashCode> classHashes = new HashMap<>(usedClasses.size());
        Map<String, DependentsSet> dependents = new HashMap<>(usedClasses.size());
        Map<String, IntSet> classesToConstants = new HashMap<>(usedClasses.size());
        for (String usedClass : usedClasses) {
            HashCode hash = this.classHashes.get(usedClass);
            if (hash != null) {
                classHashes.put(usedClass, hash);
            }
            DependentsSet dependentsSet = this.dependents.get(usedClass);
            if (dependentsSet != null) {
                if (dependentsSet.isDependencyToAll()) {
                    dependents.put(usedClass, dependentsSet);
                } else {
                    Set<String> usedAccessibleClasses = new HashSet<>(dependentsSet.getAccessibleDependentClasses());
                    usedAccessibleClasses.retainAll(usedClasses);
                    dependents.put(usedClass, DependentsSet.dependentClasses(Collections.emptySet(), usedAccessibleClasses));
                }
            }
            IntSet constants = this.classesToConstants.get(usedClass);
            if (constants != null) {
                classesToConstants.put(usedClass, constants);
            }
        }

        return new ClassSetAnalysisData(classHashes, dependents, classesToConstants, null);
    }

    public DependentsSet getChangedClassesSince(ClassSetAnalysisData other) {
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        if (other.fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(other.fullRebuildCause);
        }
        for (String added : Sets.difference(classHashes.keySet(), other.classHashes.keySet())) {
            DependentsSet dependentsOfAdded = getDependents(added);
            if (dependentsOfAdded.isDependencyToAll()) {
                return dependentsOfAdded;
            }
        }
        ImmutableSet.Builder<String> changed = ImmutableSet.builder();
        for (Map.Entry<String, HashCode> removedOrChanged : Sets.difference(other.classHashes.entrySet(), classHashes.entrySet())) {
            changed.add(removedOrChanged.getKey());
        }
        return DependentsSet.dependentClasses(ImmutableSet.of(), changed.build());
    }

    public DependentsSet getDependents(String className) {
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        DependentsSet dependentsSet = dependents.get(className);
        return dependentsSet == null ? DependentsSet.empty() : dependentsSet;
    }

    public IntSet getConstants(String className) {
        IntSet integers = classesToConstants.get(className);
        if (integers == null) {
            return IntSets.EMPTY_SET;
        }
        return integers;
    }

    public static class Serializer extends AbstractSerializer<ClassSetAnalysisData> {

        private final StringInterner interner;
        private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

        public Serializer(StringInterner interner) {
            this.interner = interner;
        }

        @Override
        public ClassSetAnalysisData read(Decoder decoder) throws Exception {
            Map<Integer, String> classNameMap = new HashMap<>();

            int count = decoder.readSmallInt();
            ImmutableMap.Builder<String, HashCode> classHashes = ImmutableMap.builderWithExpectedSize(count);
            for (int i = 0; i < count; i++) {
                String className = readAndInternClassName(decoder, classNameMap);
                HashCode hashCode = hashCodeSerializer.read(decoder);
                classHashes.put(className, hashCode);
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, DependentsSet> dependentsBuilder = ImmutableMap.builderWithExpectedSize(count);
            for (int i = 0; i < count; i++) {
                String className = readAndInternClassName(decoder, classNameMap);
                DependentsSet dependents = readDependentsSet(decoder, classNameMap);
                dependentsBuilder.put(className, dependents);
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, IntSet> classesToConstantsBuilder = ImmutableMap.builderWithExpectedSize(count);
            for (int i = 0; i < count; i++) {
                String className = readAndInternClassName(decoder, classNameMap);
                IntSet constants = IntSetSerializer.INSTANCE.read(decoder);
                classesToConstantsBuilder.put(className, constants);
            }

            String fullRebuildCause = decoder.readNullableString();

            return new ClassSetAnalysisData(classHashes.build(), dependentsBuilder.build(), classesToConstantsBuilder.build(), fullRebuildCause);
        }

        @Override
        public void write(Encoder encoder, ClassSetAnalysisData value) throws Exception {
            Map<String, Integer> classNameMap = new HashMap<>();
            encoder.writeSmallInt(value.classHashes.size());
            for (Map.Entry<String, HashCode> entry : value.classHashes.entrySet()) {
                writeClassName(entry.getKey(), classNameMap, encoder);
                hashCodeSerializer.write(encoder, entry.getValue());
            }

            encoder.writeSmallInt(value.dependents.size());
            for (Map.Entry<String, DependentsSet> entry : value.dependents.entrySet()) {
                writeClassName(entry.getKey(), classNameMap, encoder);
                writeDependentSet(entry.getValue(), classNameMap, encoder);
            }

            encoder.writeSmallInt(value.classesToConstants.size());
            for (Map.Entry<String, IntSet> entry : value.classesToConstants.entrySet()) {
                writeClassName(entry.getKey(), classNameMap, encoder);
                IntSetSerializer.INSTANCE.write(encoder, entry.getValue());
            }
            encoder.writeNullableString(value.fullRebuildCause);
        }

        private DependentsSet readDependentsSet(Decoder decoder, Map<Integer, String> classNameMap) throws IOException {
            byte b = decoder.readByte();
            if (b == 0) {
                return DependentsSet.dependencyToAll(decoder.readNullableString());
            }

            ImmutableSet.Builder<String> privateBuilder = ImmutableSet.builder();
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                privateBuilder.add(readAndInternClassName(decoder, classNameMap));
            }

            ImmutableSet.Builder<String> accessibleBuilder = ImmutableSet.builder();
            count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                accessibleBuilder.add(readAndInternClassName(decoder, classNameMap));
            }

            ImmutableSet.Builder<GeneratedResource> resourceBuilder = ImmutableSet.builder();
            count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                GeneratedResource.Location location = GeneratedResource.Location.values()[decoder.readSmallInt()];
                String path = decoder.readString();
                resourceBuilder.add(new GeneratedResource(location, path));
            }
            return DependentsSet.dependents(privateBuilder.build(), accessibleBuilder.build(), resourceBuilder.build());
        }

        private void writeDependentSet(DependentsSet dependentsSet, Map<String, Integer> classNameMap, Encoder encoder) throws IOException {
            if (dependentsSet.isDependencyToAll()) {
                encoder.writeByte((byte) 0);
                encoder.writeNullableString(dependentsSet.getDescription());
            } else {
                encoder.writeByte((byte) 1);
                encoder.writeSmallInt(dependentsSet.getPrivateDependentClasses().size());
                for (String className : dependentsSet.getPrivateDependentClasses()) {
                    writeClassName(className, classNameMap, encoder);
                }
                encoder.writeSmallInt(dependentsSet.getAccessibleDependentClasses().size());
                for (String className : dependentsSet.getAccessibleDependentClasses()) {
                    writeClassName(className, classNameMap, encoder);
                }
                encoder.writeSmallInt(dependentsSet.getDependentResources().size());
                for (GeneratedResource resource : dependentsSet.getDependentResources()) {
                    encoder.writeSmallInt(resource.getLocation().ordinal());
                    encoder.writeString(resource.getPath());
                }
            }
        }

        private String readAndInternClassName(Decoder decoder, Map<Integer, String> classNameMap) throws IOException {
            String className = readClassName(decoder, classNameMap);
            return interner.intern(className);
        }

        private String readClassName(Decoder decoder, Map<Integer, String> classNameMap) throws IOException {
            int id = decoder.readSmallInt();
            String className = classNameMap.get(id);
            if (className == null) {
                className = readFirstOccurrenceOfClass(decoder, classNameMap);
                classNameMap.put(id, className);
            }
            return className;
        }

        private String readFirstOccurrenceOfClass(Decoder decoder, Map<Integer, String> manifest) throws IOException {
            int type = decoder.readByte();
            String className;
            if (type == 0) {
                String parent = readClassName(decoder, manifest);
                String child = decoder.readString();
                className = parent + '$' + child;
            } else if (type == 1) {
                String parent = readClassName(decoder, manifest);
                String child = decoder.readString();
                className = parent + '.' + child;
            } else {
                className = decoder.readString();
            }
            return className;
        }

        private void writeClassName(String className, Map<String, Integer> classIdMap, Encoder encoder) throws IOException {
            Integer id = classIdMap.get(className);
            if (id == null) {
                id = classIdMap.size();
                classIdMap.put(className, id);
                encoder.writeSmallInt(id);
                writeFirstOccurrenceOfClass(className, classIdMap, encoder);
            } else {
                encoder.writeSmallInt(id);
            }
        }

        private void writeFirstOccurrenceOfClass(String className, Map<String, Integer> manifest, Encoder encoder) throws IOException {
            int nestedTypeSeparator = className.lastIndexOf('$');
            if (nestedTypeSeparator > 0) {
                encoder.writeByte((byte) 0);
                writeClassName(className.substring(0, nestedTypeSeparator), manifest, encoder);
                encoder.writeString(className.substring(nestedTypeSeparator + 1));
            } else {
                int packageSeparator = className.lastIndexOf('.');
                if (packageSeparator > 0) {
                    encoder.writeByte((byte) 1);
                    writeClassName(className.substring(0, packageSeparator), manifest, encoder);
                    encoder.writeString(className.substring(packageSeparator + 1));
                } else {
                    encoder.writeByte((byte) 2);
                    encoder.writeString(className);
                }
            }
        }
    }
}
