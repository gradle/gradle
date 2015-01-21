/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

class ParameterizedTypeWrapper implements ParameterizedType, TypeWrapper {

    private final TypeWrapper[] actualTypeArguments;
    private final TypeWrapper rawType;
    private final TypeWrapper ownerType;
    private final int hashCode;

    public ParameterizedTypeWrapper(TypeWrapper[] actualTypeArguments, TypeWrapper rawType, TypeWrapper ownerType, int hashCode) {
        this.actualTypeArguments = actualTypeArguments;
        this.rawType = rawType;
        this.ownerType = ownerType;
        this.hashCode = hashCode;
    }

    @Override
    public Type[] getActualTypeArguments() {
        return ModelType.unwrap(actualTypeArguments);
    }

    @Override
    public Type getRawType() {
        return rawType.unwrap();
    }

    @Override
    public Type getOwnerType() {
        return ownerType.unwrap();
    }

    @Override
    public Type unwrap() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ParameterizedType) {
            ParameterizedType that = (ParameterizedType) o;
            if (this == that) {
                return true;
            } else {
                Type ownerType = getOwnerType();
                Type rawType = getRawType();
                Type thatOwner = that.getOwnerType();
                Type thatRawType = that.getRawType();
                return (ownerType == null ? thatOwner == null : ownerType.equals(thatOwner))
                        && (rawType == null ? thatRawType == null : rawType.equals(thatRawType))
                        && Arrays.equals(getActualTypeArguments(), that.getActualTypeArguments());
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        Type ownerType = getOwnerType();
        Class<?> rawType = (Class<?>) getRawType();
        if (ownerType != null) {
            if (ownerType instanceof Class) {
                sb.append(((Class) ownerType).getName());
            } else {
                sb.append(ownerType.toString());
            }

            sb.append(".");

            if (ownerType instanceof ParameterizedTypeWrapper) {
                // Find simple name of nested type by removing the
                // shared prefix with owner.
                Class<?> ownerRaw = (Class<?>) ((ParameterizedTypeWrapper) ownerType).rawType.unwrap();
                sb.append(rawType.getName().replace(ownerRaw.getName() + "$",
                        ""));
            } else {
                sb.append(rawType.getName());
            }
        } else {
            sb.append(rawType.getName());
        }

        Type[] actualTypeArguments = getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            sb.append("<");
            boolean first = true;
            for (Type t : actualTypeArguments) {
                if (!first) {
                    sb.append(", ");
                }
                if (t instanceof Class) {
                    sb.append(((Class) t).getName());
                } else {
                    sb.append(t.toString());
                }
                first = false;
            }
            sb.append(">");
        }

        return sb.toString();
    }

    @Override
    public String getRepresentation() {
        return toString();
    }
}
