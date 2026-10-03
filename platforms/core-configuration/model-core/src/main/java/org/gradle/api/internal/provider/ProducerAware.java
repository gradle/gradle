/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.internal.state.ModelObject;

/**
 * An object that can be declared as an output property of a model object, such as a task.
 *
 * <p>The task that owns that model object produces the value of this object, so anything that consumes the value
 * carries an implicit dependency on the task. This is implemented by lazy properties and by configurable file collections.</p>
 *
 * @see OutputProperties
 */
public interface ProducerAware {
    /**
     * Associates this object with the model object that declares it as an output property, and whose task therefore produces its value.
     *
     * <p>Fails when this object has already been associated with a different model object.</p>
     */
    void attachProducer(ModelObject owner);
}
