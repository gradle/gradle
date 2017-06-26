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

import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;

import java.math.BigDecimal;

public class JacocoLimitImpl implements JacocoLimit {

    private String counter = "INSTRUCTION";
    private String value = "COVEREDRATIO";
    private BigDecimal minimum;
    private BigDecimal maximum;

    @Override
    public String getCounter() {
        return counter;
    }

    @Override
    public void setCounter(String counter) {
        this.counter = counter;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public BigDecimal getMinimum() {
        return minimum;
    }

    @Override
    public void setMinimum(BigDecimal minimum) {
        this.minimum = minimum;
    }

    @Override
    public BigDecimal getMaximum() {
        return maximum;
    }

    @Override
    public void setMaximum(BigDecimal maximum) {
        this.maximum = maximum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JacocoLimitImpl that = (JacocoLimitImpl) o;

        if (counter != that.counter) {
            return false;
        }
        if (value != that.value) {
            return false;
        }
        if (minimum != null ? !minimum.equals(that.minimum) : that.minimum != null) {
            return false;
        }
        return maximum != null ? maximum.equals(that.maximum) : that.maximum == null;
    }

    @Override
    public int hashCode() {
        int result = counter != null ? counter.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (minimum != null ? minimum.hashCode() : 0);
        result = 31 * result + (maximum != null ? maximum.hashCode() : 0);
        return result;
    }
}
