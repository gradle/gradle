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

package org.gradle.internal.instantiation;

import org.gradle.api.reflect.ObjectInstantiationException;

/**
 * Creates instance of objects in preparation for deserialization of their state.
 */
public interface DeserializationInstantiator {
    /**
     * Creates an instance of the given type without invoking its constructor. Invokes the constructor of the given base class.
     *
     * @throws ObjectInstantiationException On failure to create the new instance.
     */
    <T> T newInstance(Class<T> implType, Class<? super T> baseClass) throws ObjectInstantiationException;
}
