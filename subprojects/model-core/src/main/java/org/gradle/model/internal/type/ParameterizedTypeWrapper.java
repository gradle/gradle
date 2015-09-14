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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

class ParameterizedTypeWrapper implements ParameterizedType, TypeWrapper {

    private final TypeWrapper[] actualTypeArguments;
    private final ClassTypeWrapper rawType;
    private final TypeWrapper ownerType;
    private final int hashCode;

    public ParameterizedTypeWrapper(TypeWrapper[] actualTypeArguments, ClassTypeWrapper rawType, @Nullable TypeWrapper ownerType, int hashCode) {
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
        return ownerType == null ? null : ownerType.unwrap();
    }

    @Override
    public Type unwrap() {
        return this;
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        rawType.collectClasses(builder);
        for (TypeWrapper actualTypeArgument : actualTypeArguments) {
            actualTypeArgument.collectClasses(builder);
        }
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
        return getRepresentation(true);
    }

    @Override
    public String getRepresentation(boolean full) {
        StringBuilder sb = new StringBuilder();
        if (ownerType != null) {
            sb.append(ownerType.getRepresentation(full));
            sb.append('.');
            sb.append(rawType.unwrap().getSimpleName());
        } else {
            sb.append(rawType.getRepresentation(full));
        }
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            sb.append("<");
            boolean first = true;
            for (TypeWrapper t : actualTypeArguments) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(t.getRepresentation(full));
                first = false;
            }
            sb.append(">");
        }

        return sb.toString();
    }
}
