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

import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.IntSetSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Set;

public class ClassAnalysisSerializer extends AbstractSerializer<ClassAnalysis> {

    private final StringInterner interner;
    private final SetSerializer<String> stringSetSerializer;

    public ClassAnalysisSerializer(StringInterner interner) {
        stringSetSerializer = new SetSerializer<String>(new InterningStringSerializer(interner), false);
        this.interner = interner;
    }

    @Override
    public ClassAnalysis read(Decoder decoder) throws Exception {
        String className = interner.intern(decoder.readString());
        boolean relatedToAll = decoder.readBoolean();
        Set<String> privateClasses = stringSetSerializer.read(decoder);
        Set<String> accessibleClasses = stringSetSerializer.read(decoder);
        IntSet constants = IntSetSerializer.INSTANCE.read(decoder);
        return new ClassAnalysis(className, privateClasses, accessibleClasses, relatedToAll, constants);
    }

    @Override
    public void write(Encoder encoder, ClassAnalysis value) throws Exception {
        encoder.writeString(value.getClassName());
        encoder.writeBoolean(value.isDependencyToAll());
        stringSetSerializer.write(encoder, value.getPrivateClassDependencies());
        stringSetSerializer.write(encoder, value.getAccessibleClassDependencies());
        IntSetSerializer.INSTANCE.write(encoder, value.getConstants());
    }

}
