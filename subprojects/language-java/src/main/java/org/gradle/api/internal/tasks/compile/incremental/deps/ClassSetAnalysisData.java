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
import com.google.common.collect.Multimap;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.SetSerializer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER;

public class ClassSetAnalysisData {
    final Map<String, String> filePathToClassName;
    final Map<String, DependentsSet> dependents;
    final Map<String, Set<Integer>> classesToConstants;
    final Map<Integer, Set<String>> literalsToClasses;
    final Map<String, Set<String>> classesToChildren;

    public ClassSetAnalysisData(Map<String, String> filePathToClassName, Map<String, DependentsSet> dependents, Multimap<String, Integer> classesToConstants, Multimap<Integer, String> literalsToClasses, Multimap<String, String> classesToChildren) {
        this(filePathToClassName, dependents, asMap(classesToConstants), asMap(literalsToClasses), asMap(classesToChildren));
    }

    public ClassSetAnalysisData(Map<String, String> filePathToClassName, Map<String, DependentsSet> dependents, Map<String, Set<Integer>> classesToConstants, Map<Integer, Set<String>> literalsToClasses, Map<String, Set<String>> classesToChildren) {
        this.filePathToClassName = filePathToClassName;
        this.dependents = dependents;
        this.classesToConstants = classesToConstants;
        this.literalsToClasses = literalsToClasses;
        this.classesToChildren = classesToChildren;
    }

    private static <K, V> Map<K, Set<V>> asMap(Multimap<K, V> multimap) {
        ImmutableMap.Builder<K, Set<V>> builder = ImmutableMap.builder();
        for (K key : multimap.keySet()) {
            builder.put(key, ImmutableSet.copyOf(multimap.get(key)));
        }
        return builder.build();
    }

    public String getClassNameForFile(String filePath) {
        return filePathToClassName.get(filePath);
    }

    public DependentsSet getDependents(String className) {
        return dependents.get(className);
    }

    public Set<Integer> getConstants(String className) {
        Set<Integer> integers = classesToConstants.get(className);
        if (integers == null) {
            return Collections.emptySet();
        }
        return integers;
    }

    public Set<String> getChildren(String className) {
        Set<String> children = classesToChildren.get(className);
        return children == null ? Collections.<String>emptySet() : children;
    }

    public static class Serializer extends AbstractSerializer<ClassSetAnalysisData> {
        private static final SetSerializer<Integer> INTEGER_SET_SERIALIZER = new SetSerializer<Integer>(INTEGER_SERIALIZER, false);

        @Override
        public ClassSetAnalysisData read(Decoder decoder) throws Exception {
            // Class names are de-duplicated when encoded
            Map<Integer, String> classNameMap = new HashMap<Integer, String>();

            int count = decoder.readSmallInt();
            ImmutableMap.Builder<String, String> filePathToClassNameBuilder = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                String filePath = decoder.readString();
                String className = readClassName(decoder, classNameMap);
                filePathToClassNameBuilder.put(filePath, className);
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, DependentsSet> dependentsBuilder = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                String className = readClassName(decoder, classNameMap);
                DependentsSet dependents = readDependentsSet(decoder, classNameMap);
                dependentsBuilder.put(className, dependents);
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, Set<Integer>> classesToConstantsBuilder = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                String className = readClassName(decoder, classNameMap);
                Set<Integer> constants = INTEGER_SET_SERIALIZER.read(decoder);
                classesToConstantsBuilder.put(className, constants);
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<Integer, Set<String>> literalsToClassesBuilder = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                int literal = decoder.readInt();
                int nameCount = decoder.readSmallInt();
                ImmutableSet.Builder<String> namesBuilder = ImmutableSet.builder();
                for (int j = 0; j < nameCount; j++) {
                    namesBuilder.add(readClassName(decoder, classNameMap));
                }
                literalsToClassesBuilder.put(literal, namesBuilder.build());
            }

            count = decoder.readSmallInt();
            ImmutableMap.Builder<String, Set<String>> classNameToChildren = ImmutableMap.builder();
            for (int i = 0; i < count; i++) {
                String parent = readClassName(decoder, classNameMap);
                int nameCount = decoder.readSmallInt();
                ImmutableSet.Builder<String> namesBuilder = ImmutableSet.builder();
                for (int j = 0; j < nameCount; j++) {
                    namesBuilder.add(readClassName(decoder, classNameMap));
                }
                classNameToChildren.put(parent, namesBuilder.build());
            }

            return new ClassSetAnalysisData(filePathToClassNameBuilder.build(), dependentsBuilder.build(), classesToConstantsBuilder.build(), literalsToClassesBuilder.build(), classNameToChildren.build());
        }

        @Override
        public void write(Encoder encoder, ClassSetAnalysisData value) throws Exception {
            // Deduplicate class names when encoding.
            // This would be more efficient with a better data structure in ClassSetAnalysisData
            Map<String, Integer> classNameMap = new HashMap<String, Integer>();

            encoder.writeSmallInt(value.filePathToClassName.size());
            for (Map.Entry<String, String> entry : value.filePathToClassName.entrySet()) {
                encoder.writeString(entry.getKey());
                writeClassName(entry.getValue(), classNameMap, encoder);
            }

            encoder.writeSmallInt(value.dependents.size());
            for (Map.Entry<String, DependentsSet> entry : value.dependents.entrySet()) {
                writeClassName(entry.getKey(), classNameMap, encoder);
                writeDependentSet(entry.getValue(), classNameMap, encoder);
            }

            encoder.writeSmallInt(value.classesToConstants.size());
            for (Map.Entry<String, Set<Integer>> entry : value.classesToConstants.entrySet()) {
                writeClassName(entry.getKey(), classNameMap, encoder);
                INTEGER_SET_SERIALIZER.write(encoder, entry.getValue());
            }

            encoder.writeSmallInt(value.literalsToClasses.size());
            for (Map.Entry<Integer, Set<String>> entry : value.literalsToClasses.entrySet()) {
                encoder.writeInt(entry.getKey());
                encoder.writeSmallInt(entry.getValue().size());
                for (String className : entry.getValue()) {
                    writeClassName(className, classNameMap, encoder);
                }
            }

            encoder.writeSmallInt(value.classesToChildren.size());
            for (Map.Entry<String, Set<String>> entry : value.classesToChildren.entrySet()) {
                writeClassName(entry.getKey(), classNameMap, encoder);
                encoder.writeSmallInt(entry.getValue().size());
                for (String className : entry.getValue()) {
                    writeClassName(className, classNameMap, encoder);
                }
            }
        }

        private DependentsSet readDependentsSet(Decoder decoder, Map<Integer, String> classNameMap) throws IOException {
            byte b = decoder.readByte();
            if (b == 1) {
                return new DependencyToAll(decoder.readNullableString());
            }
            int count = decoder.readSmallInt();
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for (int i = 0; i < count; i++) {
                builder.add(readClassName(decoder, classNameMap));
            }
            return new DefaultDependentsSet(builder.build());
        }

        private void writeDependentSet(DependentsSet dependentsSet, Map<String, Integer> classNameMap, Encoder encoder) throws IOException {
            if (dependentsSet.isDependencyToAll()) {
                encoder.writeByte((byte) 1);
                encoder.writeNullableString(dependentsSet.getDescription());
            } else {
                encoder.writeByte((byte) 2);
                encoder.writeSmallInt(dependentsSet.getDependentClasses().size());
                for (String className : dependentsSet.getDependentClasses()) {
                    writeClassName(className, classNameMap, encoder);
                }
            }
        }

        private String readClassName(Decoder decoder, Map<Integer, String> classNameMap) throws IOException {
            int id = decoder.readSmallInt();
            if (id == 0) {
                id = decoder.readSmallInt();
                String className = decoder.readString();
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
