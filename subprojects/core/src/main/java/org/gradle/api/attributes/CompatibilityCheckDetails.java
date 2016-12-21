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

import org.gradle.api.Incubating;

/**
 * Provides context about attribute compatibility checks, and allows the user to define
 * when an attribute is compatible with another.
 *
 * @param <T> the concrete type of the attribute
 *
 * @since 3.3
 */
@Incubating
public interface CompatibilityCheckDetails<T> {
    /**
     * The value of the attribute as found on the consumer side.
     * @return the value from the consumer
     */
    T getConsumerValue();

    /**
     * The value of the attribute as found on the producer side.
     * @return the value from the producer
     */
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
