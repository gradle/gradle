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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.IntSetSerializer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides information about a set of classes, e.g. a JAR or a whole classpath.
 * Contains a hash for every class contained in the set, so it can determine which classes have changed compared to another set.
 * Contains a reverse dependency view, so we can determine which classes in this set are affected by a change to a class inside or outside this set.
 * Contains information about the accessible, inlineable constants in each class, since these require full recompilation of dependents if changed.
 * If analysis failed for any reason, that reason is captured and triggers full rebuilds if this class set is used.
 *
 * @see ClassSetAnalysis for the logic that calculates transitive dependencies.
 */
public class ClassSetAnalysisData {

    private static final String MODULE_INFO = "module-info";
    private static final String PACKAGE_INFO = "package-info";

    /**
     * Merges the given class sets, applying classpath shadowing semantics. I.e. only the first occurrency of each class will be kept.
     */
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

    /**
     * Returns a shrunk down version of this class set, which only contains information about types that could affect the other set.
     * This is useful for reducing the size of classpath snapshots, since a classpath usually contains a lot more types than the client
     * actually uses.
     *
     * Apart from the obvious classes that are directly used by the other set, we also need to keep any classes that might affect any number
     * of classes, like package-info, module-info or classes with inlineable constants.
     */
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
        for (String cls : classHashes.keySet()) {
            if (cls.endsWith(PACKAGE_INFO)) {
                usedClasses.add(cls);
            }
        }
        usedClasses.addAll(classesToConstants.keySet());
        usedClasses.addAll(other.dependents.keySet());

        Multimap<String, String> dependencies = getForwardDependencyView();

        Set<String> visited = new HashSet<>(usedClasses.size());
        Deque<String> pending = new ArrayDeque<>(usedClasses);
        while (!pending.isEmpty()) {
            String cls = pending.poll();
            if (visited.add(cls)) {
                usedClasses.add(cls);
                pending.addAll(dependencies.get(cls));
            }
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

    /**
     * Takes the reverse dependency view of this set and reverses it, so it turns into a forward dependency view.
     * Excludes types that are dependencies to all others, these need to be handled separately by the caller.
     */
    private Multimap<String, String> getForwardDependencyView() {
        Multimap<String, String> dependencies = ArrayListMultimap.create(dependents.size(), 10);
        for (Map.Entry<String, DependentsSet> entry : dependents.entrySet()) {
            if (entry.getValue().isDependencyToAll()) {
                continue;
            }
            for (String dependent : entry.getValue().getAccessibleDependentClasses()) {
                dependencies.put(dependent, entry.getKey());
            }
        }
        return dependencies;
    }

    /**
     * Returns the additions, changes and removals compared to the other class set.
     * Only includes changes that could possibly trigger recompilation.
     * For example, adding a new class can't affect anyone, since it didn't exist before.
     *
     * Does not include classes that are transitively affected by the additions/removals/changes.
     */
    public DependentsSet getChangedClassesSince(ClassSetAnalysisData other) {
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        if (other.fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(other.fullRebuildCause);
        }

        ImmutableSet.Builder<String> changed = ImmutableSet.builder();
        for (String added : Sets.difference(classHashes.keySet(), other.classHashes.keySet())) {
            DependentsSet dependents = getDependents(added);
            if (dependents.isDependencyToAll()) {
                return dependents;
            }
            if (added.endsWith(PACKAGE_INFO)) {
                changed.add(added);
            }
        }
        for (Map.Entry<String, HashCode> removedOrChanged : Sets.difference(other.classHashes.entrySet(), classHashes.entrySet())) {
            DependentsSet dependents = getDependents(removedOrChanged.getKey());
            if (dependents.isDependencyToAll()) {
                return dependents;
            }
            changed.add(removedOrChanged.getKey());
        }
        return DependentsSet.dependentClasses(ImmutableSet.of(), changed.build());
    }

    /**
     * Returns the dependents that directly depend on the given class.
     */
    public DependentsSet getDependents(String className) {
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        if (className.equals(MODULE_INFO)) {
            return DependentsSet.dependencyToAll("module-info has changed");
        }
        if (className.endsWith(PACKAGE_INFO)) {
            String packageName = className.equals(PACKAGE_INFO) ? null : StringUtils.removeEnd(className, "." + PACKAGE_INFO);
            return getDependentsOfPackage(packageName);
        }
        DependentsSet dependentsSet = dependents.get(className);
        return dependentsSet == null ? DependentsSet.empty() : dependentsSet;
    }

    private DependentsSet getDependentsOfPackage(String packageName) {
        Set<String> typesInPackage = new HashSet<>();
        for (String type : classHashes.keySet()) {
            int i = type.lastIndexOf(".");
            if (i < 0 && packageName == null || i > 0 && type.substring(0, i).equals(packageName)) {
                typesInPackage.add(type);
            }
        }
        return DependentsSet.dependentClasses(Collections.emptySet(), typesInPackage);
    }

    /**
     * Gets the accessible, inlineable constants of the given class.
     */
    public IntSet getConstants(String className) {
        IntSet integers = classesToConstants.get(className);
        if (integers == null) {
            return IntSets.EMPTY_SET;
        }
        return integers;
    }

    /**
     * This serializer is optimized for the highly repetitive nature of class names.
     * It only serializes each class name once and stores a reference to that name when it comes up again.
     * Names are stored in a hierarchical format, to take advantage of the fact that most classes share a package prefix.
     */
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
                return DependentsSet.dependencyToAll(decoder.readString());
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
                encoder.writeString(dependentsSet.getDescription());
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
