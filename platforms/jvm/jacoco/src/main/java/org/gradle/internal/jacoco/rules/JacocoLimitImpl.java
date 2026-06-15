/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jacoco.rules;

import org.gradle.api.provider.Property;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

public abstract class JacocoLimitImpl implements JacocoLimit {

    public JacocoLimitImpl() {
        getCounter().convention("INSTRUCTION");
        getValue().convention("COVEREDRATIO");
    }

    @Override
    public abstract Property<String> getCounter();

    @Override
    public void setCounter(String counter) {
        getCounter().set(counter);
    }

    @Override
    public abstract Property<String> getValue();

    @Override
    public void setValue(String value) {
        getValue().set(value);
    }

    @Override
    public abstract Property<BigDecimal> getMinimum();

    @Override
    public void setMinimum(@Nullable BigDecimal minimum) {
        getMinimum().set(minimum);
    }

    @Override
    public abstract Property<BigDecimal> getMaximum();

    @Override
    public void setMaximum(@Nullable BigDecimal maximum) {
        getMaximum().set(maximum);
    }
}
