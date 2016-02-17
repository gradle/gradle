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

import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class ClassAnalysisSerializer implements Serializer<ClassAnalysis> {

    private SetSerializer<String> setSerializer = new SetSerializer<String>(STRING_SERIALIZER, false);

    @Override
    public ClassAnalysis read(Decoder decoder) throws Exception {
        boolean relatedToAll = decoder.readBoolean();
        Set<String> classes = setSerializer.read(decoder);
        return new ClassAnalysis(classes, relatedToAll);
    }

    @Override
    public void write(Encoder encoder, ClassAnalysis value) throws Exception {
        encoder.writeBoolean(value.isDependencyToAll());
        setSerializer.write(encoder, value.getClassDependencies());
    }
}
