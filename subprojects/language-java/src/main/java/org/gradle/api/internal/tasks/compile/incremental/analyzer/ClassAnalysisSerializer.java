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

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class ClassAnalysisSerializer implements Serializer<ClassAnalysis> {

    private SetSerializer<String> stringSetSerializer = new SetSerializer<String>(STRING_SERIALIZER, false);
    private SetSerializer<Integer> integerSetSerializer = new SetSerializer<Integer>(INTEGER_SERIALIZER, false);

    @Override
    public ClassAnalysis read(Decoder decoder) throws Exception {
        boolean relatedToAll = decoder.readBoolean();
        Set<String> classes = stringSetSerializer.read(decoder);
        Set<Integer> constants = integerSetSerializer.read(decoder);
        Set<Integer> literals = integerSetSerializer.read(decoder);
        return new ClassAnalysis(classes, relatedToAll, constants, literals);
    }

    @Override
    public void write(Encoder encoder, ClassAnalysis value) throws Exception {
        encoder.writeBoolean(value.isDependencyToAll());
        stringSetSerializer.write(encoder, value.getClassDependencies());
        integerSetSerializer.write(encoder, value.getConstants());
        integerSetSerializer.write(encoder, value.getLiterals());
    }

}
