/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.java.archives.internal.generators;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.internal.Reflection;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TupleGenerator<T> extends Generator<T> {
    private final List<Field> fields;
    private final Class<T> type;
    private final List<Generator<Object>> generators = new ArrayList<>();

    /**
     * @param type the type of objects to be generated
     */
    public TupleGenerator(Class<T> type) {
        super(type);
        fields = Reflection.allDeclaredFieldsOf(type)
            .stream()
            .filter(f -> !Modifier.isFinal(f.getModifiers()))
            .collect(Collectors.toList());
        this.type = type;
    }

    @Override
    public void configure(AnnotatedType annotatedType) {
        super.configure(annotatedType);
        generators.clear();
        for (Field field : fields) {
            @SuppressWarnings("unchecked")
            Generator<Object> generator = (Generator<Object>) gen().field(field);
            generators.add(generator);
        }
    }

    @Override
    public boolean canShrink(Object larger) {
        return type.isInstance(larger);
    }

    @Override
    public BigDecimal magnitude(Object value) {
        BigDecimal result = BigDecimal.ZERO;
        for (int i = 0; i < fields.size(); i++) {
            Object field;
            try {
                field = fields.get(i).get(value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to get field " + fields.get(i), e);
            }
            result = result.add(generators.get(i).magnitude(field));
        }
        return result;
    }

    @Override
    public List<T> doShrink(SourceOfRandomness random, T larger) {
        List<T> res = new ArrayList<>();
        // Shrink all the items
        // Note each component might produce its own shrink list
        // We don't want to perform "cartesian join", so we sample
        // several items from that list
        List<List<Object>> newFields = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            Object old;
            try {
                old = fields.get(i).get(larger);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to get field " + fields.get(i), e);
            }
            List<Object> newValue = null;
            Generator<Object> generator = generators.get(i);
            if (generator.canShrink(old)) {
                newValue = generator.shrink(random, old);
            }
            if (newValue == null || newValue.isEmpty()) {
                newValue = Collections.singletonList(old);
            }
            newFields.add(newValue);
        }
        for (int k = 0; k < 100; k++) {
            T value = Reflection.instantiate(type);
            for (int i = 0; i < fields.size(); i++) {
                Object newValue;
                if (k == 0) {
                    newValue = newFields.get(i).get(0);
                } else {
                    newValue = random.choose(newFields.get(i));
                }
                try {
                    fields.get(i).set(value, newValue);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Unable to set field " + fields.get(i), e);
                }
            }
            res.add(value);
        }
        return res;
    }

    @Override
    public T generate(SourceOfRandomness random, GenerationStatus status) {
        T result = Reflection.instantiate(type);
        for (int i = 0; i < generators.size(); i++) {
            Generator<Object> gen = generators.get(i);
            Object value = gen.generate(random, status);
            try {
                fields.get(i).set(result, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to set field " + fields.get(i), e);
            }
        }
        return result;
    }
}
