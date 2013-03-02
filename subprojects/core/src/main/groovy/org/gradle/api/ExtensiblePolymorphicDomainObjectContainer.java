/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api;

/**
 * A {@link org.gradle.api.PolymorphicDomainObjectContainer} that can be extended at runtime to
 * create elements of new types.
 *
 * @param <T> the (base) container element type
 */
@Incubating
public interface ExtensiblePolymorphicDomainObjectContainer<T> extends PolymorphicDomainObjectContainer<T> {
    /**
     * Registers a factory for creating elements of the specified type. Typically, the specified type
     * is an interface type.
     *
     * @param type the type of objects created by the factory
     * @param factory the factory to register
     * @param <U> the type of objects created by the factory
     *
     * @throws IllegalArgumentException if the specified type is not a subtype of the container element type
     */
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory);
}
