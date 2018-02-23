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

package org.gradle.api.internal.tasks.compile.incremental.analyzer;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.IntSetSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class ClassAnalysisSerializer extends AbstractSerializer<ClassAnalysis> {

    private static final SetSerializer<String> STRING_SET_SERIALIZER = new SetSerializer<String>(STRING_SERIALIZER, false);

    @Override
    public ClassAnalysis read(Decoder decoder) throws Exception {
        String className = decoder.readString();
        boolean relatedToAll = decoder.readBoolean();
        Set<String> classes = STRING_SET_SERIALIZER.read(decoder);
        IntSet constants = IntSetSerializer.INSTANCE.read(decoder);
        Set<String> superTypes = STRING_SET_SERIALIZER.read(decoder);
        return new ClassAnalysis(className, classes, relatedToAll, constants, superTypes);
    }

    @Override
    public void write(Encoder encoder, ClassAnalysis value) throws Exception {
        encoder.writeString(value.getClassName());
        encoder.writeBoolean(value.isDependencyToAll());
        STRING_SET_SERIALIZER.write(encoder, value.getClassDependencies());
        IntSetSerializer.INSTANCE.write(encoder, value.getConstants());
        STRING_SET_SERIALIZER.write(encoder, value.getSuperTypes());
    }

}
