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

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Map;
import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class ClassSetAnalysisData {

    final Map<String, DependentsSet> dependents;

    public ClassSetAnalysisData(Map<String, DependentsSet> dependents) {
        this.dependents = dependents;
    }

    public DependentsSet getDependents(String className) {
        return dependents.get(className);
    }

    public static class Serializer implements org.gradle.internal.serialize.Serializer<ClassSetAnalysisData> {

        private final MapSerializer<String, DependentsSet> serializer = new MapSerializer<String, DependentsSet>(
                STRING_SERIALIZER, new DependentsSetSerializer());

        @Override
        public ClassSetAnalysisData read(Decoder decoder) throws Exception {
            //we only support one kind of data
            return new ClassSetAnalysisData(serializer.read(decoder));
        }

        @Override
        public void write(Encoder encoder, ClassSetAnalysisData value) throws Exception {
            //we only support one kind of data
            serializer.write(encoder, value.dependents);
        }

        private static class DependentsSetSerializer implements org.gradle.internal.serialize.Serializer<DependentsSet> {

            private SetSerializer<String> setSerializer = new SetSerializer<String>(STRING_SERIALIZER, false);

            @Override
            public DependentsSet read(Decoder decoder) throws Exception {
                int control = decoder.readSmallInt();
                if (control == 0) {
                    return new DependencyToAll();
                }
                if (control != 1 && control != 2) {
                    throw new IllegalArgumentException("Unable to read the data. Unexpected control value: " + control);
                }
                Set<String> classes = setSerializer.read(decoder);
                return new DefaultDependentsSet(control == 1, classes);
            }

            @Override
            public void write(Encoder encoder, DependentsSet value) throws Exception {
                if (value instanceof DependencyToAll) {
                    encoder.writeSmallInt(0);
                } else if (value instanceof DefaultDependentsSet) {
                    encoder.writeSmallInt(value.isDependencyToAll() ? 1 : 2);
                    setSerializer.write(encoder, value.getDependentClasses());
                } else {
                    throw new IllegalArgumentException("Don't know how to serialize value of type: " + value.getClass() + ", value: " + value);
                }
            }
        }
    }
}
