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

import org.gradle.testing.jacoco.tasks.rules.JacocoThreshold;
import org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric;
import org.gradle.testing.jacoco.tasks.rules.JacocoThresholdType;

public class JacocoThresholdImpl implements JacocoThreshold {

    private JacocoThresholdMetric metric = JacocoThresholdMetric.INSTRUCTION;
    private JacocoThresholdType type = JacocoThresholdType.COVEREDRATIO;
    private Double minimum;
    private Double maximum;

    @Override
    public JacocoThresholdMetric getMetric() {
        return metric;
    }

    @Override
    public void setMetric(JacocoThresholdMetric metric) {
        this.metric = metric;
    }

    @Override
    public JacocoThresholdType getType() {
        return type;
    }

    @Override
    public void setType(JacocoThresholdType type) {
        this.type = type;
    }

    @Override
    public Double getMinimum() {
        return minimum;
    }

    @Override
    public void setMinimum(Double minimum) {
        this.minimum = minimum;
    }

    @Override
    public Double getMaximum() {
        return maximum;
    }

    @Override
    public void setMaximum(Double maximum) {
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

        JacocoThresholdImpl that = (JacocoThresholdImpl) o;

        if (metric != that.metric) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (minimum != null ? !minimum.equals(that.minimum) : that.minimum != null) {
            return false;
        }
        return maximum != null ? maximum.equals(that.maximum) : that.maximum == null;
    }

    @Override
    public int hashCode() {
        int result = metric != null ? metric.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (minimum != null ? minimum.hashCode() : 0);
        result = 31 * result + (maximum != null ? maximum.hashCode() : 0);
        return result;
    }
}
