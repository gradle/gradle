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

import java.math.BigDecimal;

public abstract class JacocoLimitImpl implements JacocoLimit {

    public JacocoLimitImpl() {
        getCounter().convention("INSTRUCTION");
        getValue().convention("COVEREDRATIO");
    }

    @Override
    public abstract Property<String> getCounter();

    @Override
    public abstract Property<String> getValue();

    @Override
    public abstract Property<BigDecimal> getMinimum();

    @Override
    public abstract Property<BigDecimal> getMaximum();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JacocoLimitImpl that = (JacocoLimitImpl) o;

        if (getCounter().get().equals(that.getCounter().get())) {
            return false;
        }
        if (getValue().get().equals(that.getValue().get())) {
            return false;
        }
        if (getMinimum().isPresent() ? !getMinimum().equals(that.getMinimum().getOrNull()) : !that.getMinimum().isPresent()) {
            return false;
        }
        return getMaximum().isPresent() ? !getMaximum().equals(that.getMaximum().getOrNull()) : !that.getMaximum().isPresent();
    }

    @Override
    public int hashCode() {
        int result = getCounter().get().hashCode();
        result = 31 * result + getValue().get().hashCode();
        result = 31 * result + (getMinimum().isPresent() ? getMinimum().get().hashCode() : 0);
        result = 31 * result + (getMaximum().isPresent() ? getMaximum().get().hashCode() : 0);
        return result;
    }
}
