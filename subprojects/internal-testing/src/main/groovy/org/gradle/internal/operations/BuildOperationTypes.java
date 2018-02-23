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

package org.gradle.internal.operations;


import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.gradle.internal.Cast.uncheckedCast;

public final class BuildOperationTypes {

    private BuildOperationTypes() {
    }

    public static <D, R, T extends BuildOperationType<D, R>> Class<D> detailsType(Class<T> type) {
        return uncheckedCast(extract(type, 0));
    }

    public static <D, R, T extends BuildOperationType<D, R>> Class<R> resultType(Class<T> type) {
        return uncheckedCast(extract(type, 1));
    }

    private static <T extends BuildOperationType<?, ?>> Class<?> extract(Class<T> type, int index) {
        assert BuildOperationType.class.isAssignableFrom(type) : type.getName() + " is not a " + BuildOperationType.class.getName();

        for (Type superType : type.getGenericInterfaces()) {
            if (superType instanceof ParameterizedType && ((ParameterizedType) superType).getRawType().equals(BuildOperationType.class)) {

                ParameterizedType parameterizedSuperType = (ParameterizedType) superType;

                Type typeParameter = parameterizedSuperType.getActualTypeArguments()[index];
                assert typeParameter instanceof Class;

                return (Class<?>) typeParameter;
            }
        }

        throw new IllegalStateException("Failed to extract type from " + type);
    }

}
