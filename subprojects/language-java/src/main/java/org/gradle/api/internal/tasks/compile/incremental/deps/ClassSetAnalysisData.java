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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.IntSetSerializer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClassSetAnalysisData {
    public static final String PACKAGE_INFO = "package-info";

    private final Set<String> classes;
    private final Map<String, DependentsSet> dependents;
    private final Map<String, IntSet> classesToConstants;
    private final String fullRebuildCause;

    public ClassSetAnalysisData(Set<String> classes, Map<String, DependentsSet> dependents, Map<String, IntSet> classesToConstants, String fullRebuildCause) {
        this.classes = classes;
        this.dependents = dependents;
        this.classesToConstants = classesToConstants;
        this.fullRebuildCause = fullRebuildCause;
    }

    public DependentsSet getDependents(String className) {
        if (fullRebuildCause != null) {
            return DependentsSet.dependencyToAll(fullRebuildCause);
        }
        if (className.endsWith(PACKAGE_INFO)) {
            String packageName = className.equals(PACKAGE_INFO) ? null : StringUtils.removeEnd(className, "." + PACKAGE_INFO);
            return getDependentsOfPackage(packageName);
        }
        DependentsSet dependentsSet = dependents.get(className);
        return dependentsSet == null ? DependentsSet.empty() : dependentsSet;
    }

    private DependentsSet getDependentsOfPackage(String packageName) {
        Set<String> typesInPackage = Sets.newHashSet();
        for (String type : classes) {
            int i = type.lastIndexOf(".");
            if (i < 0 && packageName == null || i > 0 && type.substring(0, i).equals(packageName)) {
                typesInPackage.add(type);
            }
        }
        return DependentsSet.dependentClasses(Collections.emptySet(), typesInPackage);
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

        public Serializer(StringInterner interner) {
            this.interner = interner;
        }

        @Override
        public ClassSetAnalysisData read(Decoder decoder) throws Exception {
            Map<Integer, String> classNameMap = new HashMap<Integer, String>();

            int count = decoder.readSmallInt();
            ImmutableSet.Builder<String> classes = ImmutableSet.builder();
            for (int i = 0; i < count; i++) {
                classes.add(readClassName(decoder, classNameMap));
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, DependentsSet> dependentsBuilder = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                String className = readClassName(decoder, classNameMap);
                DependentsSet dependents = readDependentsSet(decoder, classNameMap);
                dependentsBuilder.put(className, dependents);
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, IntSet> classesToConstantsBuilder = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                String className = readClassName(decoder, classNameMap);
                IntSet constants = IntSetSerializer.INSTANCE.read(decoder);
                classesToConstantsBuilder.put(className, constants);
            }

            String fullRebuildCause = decoder.readNullableString();

            return new ClassSetAnalysisData(classes.build(), dependentsBuilder.build(), classesToConstantsBuilder.build(), fullRebuildCause);
        }

        @Override
        public void write(Encoder encoder, ClassSetAnalysisData value) throws Exception {
            Map<String, Integer> classNameMap = new HashMap<String, Integer>();
            encoder.writeSmallInt(value.classes.size());
            for (String clazz : value.classes) {
                writeClassName(clazz, classNameMap, encoder);
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
            if (b == 1) {
                return DependentsSet.dependencyToAll(decoder.readNullableString());
            }

            ImmutableSet.Builder<String> privateBuilder = ImmutableSet.builder();
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                privateBuilder.add(readClassName(decoder, classNameMap));
            }

            ImmutableSet.Builder<String> accessibleBuilder = ImmutableSet.builder();
            count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                accessibleBuilder.add(readClassName(decoder, classNameMap));
            }

            return DependentsSet.dependentClasses(privateBuilder.build(), accessibleBuilder.build());
        }

        private void writeDependentSet(DependentsSet dependentsSet, Map<String, Integer> classNameMap, Encoder encoder) throws IOException {
            if (dependentsSet.isDependencyToAll()) {
                encoder.writeByte((byte) 1);
                encoder.writeNullableString(dependentsSet.getDescription());
            } else {
                encoder.writeByte((byte) 2);
                encoder.writeSmallInt(dependentsSet.getPrivateDependentClasses().size());
                for (String className : dependentsSet.getPrivateDependentClasses()) {
                    writeClassName(className, classNameMap, encoder);
                }
                encoder.writeSmallInt(dependentsSet.getAccessibleDependentClasses().size());
                for (String className : dependentsSet.getAccessibleDependentClasses()) {
                    writeClassName(className, classNameMap, encoder);
                }
            }
        }

        private String readClassName(Decoder decoder, Map<Integer, String> classNameMap) throws IOException {
            int id = decoder.readSmallInt();
            if (id == 0) {
                id = decoder.readSmallInt();
                String className = interner.intern(decoder.readString());
                classNameMap.put(id, className);
                return className;
            }
            return classNameMap.get(id);
        }

        private void writeClassName(String className, Map<String, Integer> classIdMap, Encoder encoder) throws IOException {
            Integer id = classIdMap.get(className);
            if (id == null) {
                id = classIdMap.size() + 1;
                classIdMap.put(className, id);
                encoder.writeSmallInt(0);
                encoder.writeSmallInt(id);
                encoder.writeString(className);
            } else {
                encoder.writeSmallInt(id);
            }
        }
    }
}
