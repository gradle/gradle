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

package org.gradle.api.internal.provider;

import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

public class DefaultProvider<T> extends AbstractMinimalProvider<T> {
    private final Callable<? extends T> value;

    public DefaultProvider(Callable<? extends T> value) {
        this.value = value;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        // guard against https://youtrack.jetbrains.com/issue/KT-36297
        try {
            return inferTypeFromCallableGenericArgument();
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @Nullable
    private Class<T> inferTypeFromCallableGenericArgument() {
        // We could do a better job of figuring this out
        // Extract the type for common case that is quick to calculate
        for (Type superType : value.getClass().getGenericInterfaces()) {
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
        return null;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try {
            return Value.ofNullable(value.call());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
