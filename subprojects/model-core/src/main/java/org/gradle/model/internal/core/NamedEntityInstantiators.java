/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core;

public class NamedEntityInstantiators {
    public static <S> NamedEntityInstantiator<S> nonSubtype(Class<S> nonSubtype, final Class<?> baseClass) {
        return new NamedEntityInstantiator<S>() {
            @Override
            public <D extends S> D create(String name, Class<D> type) {
                throw new IllegalArgumentException(String.format("Cannot create an item of type %s as this is not a subtype of %s.", type.getName(), baseClass.getName()));
            }
        };
    }
}
