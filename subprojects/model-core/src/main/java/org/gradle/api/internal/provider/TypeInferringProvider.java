/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.Cast;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

public interface TypeInferringProvider<T> extends ProviderInternal<T> {
    Callable<? extends T> getCallable();

    @Nullable
    @Override
    default Class<T> getType() {
        // guard against https://youtrack.jetbrains.com/issue/KT-36297
        try {
            // We could do a better job of figuring this out
            // Extract the type for common case that is quick to calculate
            for (Type superType : getCallable().getClass().getGenericInterfaces()) {
                if (superType instanceof ParameterizedType) {
                    ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
                    if (parameterizedSuperType.getRawType().equals(Callable.class)) {
                        Type argument = parameterizedSuperType.getActualTypeArguments()[0];
                        if (argument instanceof Class) {
                            return Cast.uncheckedCast(argument);
                        }
                    }
                }
            }
        } catch (NoClassDefFoundError e) {
            return null;
        }
        return null;
    }
}
