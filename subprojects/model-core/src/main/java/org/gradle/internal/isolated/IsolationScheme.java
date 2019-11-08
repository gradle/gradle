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

package org.gradle.internal.isolated;

import com.google.common.reflect.TypeToken;
import org.gradle.internal.Cast;

import java.lang.reflect.ParameterizedType;

public class IsolationScheme<IMPLEMENTATION, PARAMS> {
    private final Class<IMPLEMENTATION> interfaceType;

    public IsolationScheme(Class<IMPLEMENTATION> interfaceType) {
        this.interfaceType = interfaceType;
    }

    public <T extends IMPLEMENTATION, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType) {
        ParameterizedType superType = (ParameterizedType) TypeToken.of(implementationType).getSupertype(interfaceType).getType();
        return Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[0]).getRawType());
    }
}
