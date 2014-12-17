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
import java.util.Objects;

class ParameterizedTypeImpl implements ParameterizedType, TypeWrapper {

    private final TypeWrapper[] actualTypeArguments;
    private final TypeWrapper rawType;
    private final TypeWrapper ownerType;
    private final int hashCode;

    public ParameterizedTypeImpl(TypeWrapper[] actualTypeArguments, TypeWrapper rawType, TypeWrapper ownerType, int hashCode) {
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
            ParameterizedType var2 = (ParameterizedType) o;
            if (this == var2) {
                return true;
            } else {
                Type var3 = var2.getOwnerType();
                Type var4 = var2.getRawType();
                return Objects.equals(getOwnerType(), var3) && Objects.equals(getRawType(), var4) && Arrays.equals(getActualTypeArguments(), var2.getActualTypeArguments());
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
    public String getTypeName() {
        StringBuilder var1 = new StringBuilder();

        Type ownerType = getOwnerType();
        Class<?> rawType = (Class<?>) getRawType();
        if (ownerType != null) {
            if (ownerType instanceof Class) {
                var1.append(((Class) ownerType).getName());
            } else {
                var1.append(ownerType.toString());
            }

            var1.append(".");

            if (ownerType instanceof ParameterizedTypeImpl) {
                Class<?> ownerClass = (Class<?>) ((ParameterizedTypeImpl) ownerType).getRawType();
                var1.append(rawType.getName().replace(ownerClass.getName() + "$", ""));
            } else {
                var1.append(rawType.getName());
            }
        } else {
            var1.append(rawType.getName());
        }

        Type[] actualTypeArguments = getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            var1.append("<");
            boolean var2 = true;
            int var4 = actualTypeArguments.length;

            for (int var5 = 0; var5 < var4; ++var5) {
                Type var6 = actualTypeArguments[var5];
                if (!var2) {
                    var1.append(", ");
                }

                var1.append(var6.getTypeName());
                var2 = false;
            }

            var1.append(">");
        }

        return var1.toString();
    }

    @Override
    public String toString() {
        return getTypeName();
    }
}
