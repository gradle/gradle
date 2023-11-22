/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.internal.attributes.CompatibilityCheckResult;

import javax.annotation.Nullable;
import java.util.Objects;

public class DefaultCompatibilityCheckResult<T> implements CompatibilityCheckResult<T> {
    private final T consumerValue;
    private final T producerValue;
    private boolean compatible;
    private boolean done;

    public DefaultCompatibilityCheckResult(@Nullable T consumerValue, T producerValue) {
        assert producerValue != null : "Internal contract of the current implementation, can be changed with a motivation";
        assert !Objects.equals(consumerValue, producerValue);
        this.consumerValue = consumerValue;
        this.producerValue = producerValue;
    }

    @Override
    public boolean isCompatible() {
        assert done;
        return compatible;
    }

    @Override
    public boolean hasResult() {
        return done;
    }

    @Override
    public T getConsumerValue() {
        return consumerValue;
    }

    @Override
    public T getProducerValue() {
        return producerValue;
    }

    @Override
    public void compatible() {
        done = true;
        compatible = true;
    }

    @Override
    public void incompatible() {
        done = true;
        compatible = false;
    }
}
