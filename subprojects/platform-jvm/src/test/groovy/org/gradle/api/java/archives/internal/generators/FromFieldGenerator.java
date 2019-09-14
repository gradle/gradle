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
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.math.BigDecimal;
import java.util.List;

public class FromFieldGenerator<T> extends Generator<T> {
    Generator<T> generator;

    public FromFieldGenerator(Class<T> type) {
        super(type);
    }

    public void configure(FromField fromField) {
        @SuppressWarnings("unchecked")
        Generator<T> generator = (Generator<T>) gen().field(fromField.value(), fromField.field());

        this.generator = generator;
    }

    @Override
    public boolean canShrink(Object larger) {
        return generator.canShrink(larger);
    }

    @Override
    public List<T> doShrink(SourceOfRandomness random, T larger) {
        return generator.doShrink(random, larger);
    }

    @Override
    public BigDecimal magnitude(Object value) {
        return generator.magnitude(value);
    }

    @Override
    public T generate(SourceOfRandomness random, GenerationStatus status) {
        if (generator == null) {
            throw new IllegalArgumentException("@FromField is not set for " + this + ", " + types());
        }
        return generator.generate(random, status);
    }
}
