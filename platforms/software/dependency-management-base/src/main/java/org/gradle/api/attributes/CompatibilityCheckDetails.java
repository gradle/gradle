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
package org.gradle.api.attributes;

import javax.annotation.Nullable;

/**
 * Provides context about attribute compatibility checks, and allows the user to define
 * when an attribute is compatible with another.
 * <p>
 * A compatibility check will <em>never</em> be performed when the consumer and producer values are equal.
 *
 * @param <T> the concrete type of the attribute
 *
 * @since 3.3
 */
public interface CompatibilityCheckDetails<T> {
    /**
     * The value of the attribute as found on the consumer side.
     * <p>
     * Never equal to the {@link #getProducerValue()}.
     *
     * @return the value from the consumer
     */
    @Nullable
    T getConsumerValue();

    /**
     * The value of the attribute as found on the producer side.
     * <p>
     * Never equal to the {@link #getConsumerValue()}.
     *
     * @return the value from the producer
     */
    @Nullable
    T getProducerValue();

    /**
     * Calling this method will indicate that the attributes are compatible.
     */
    void compatible();

    /**
     * Calling this method will indicate that the attributes are incompatible.
     */
    void incompatible();
}
