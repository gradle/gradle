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

import javax.annotation.Nullable;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;

class ParameterizedTypeWrapper implements TypeWrapper {

    private final TypeWrapper[] actualTypeArguments;
    private final ClassTypeWrapper rawType;
    private final TypeWrapper ownerType;
    private final int hashCode;

    public ParameterizedTypeWrapper(TypeWrapper[] actualTypeArguments, ClassTypeWrapper rawType, @Nullable TypeWrapper ownerType) {
        this.actualTypeArguments = actualTypeArguments;
        this.rawType = rawType;
        this.ownerType = ownerType;
        this.hashCode = hashCode(actualTypeArguments, rawType, ownerType);
    }

    private int hashCode(TypeWrapper[] actualTypeArguments, ClassTypeWrapper rawType, TypeWrapper ownerType) {
        int hashCode = rawType.hashCode();
        for (TypeWrapper actualTypeArgument : actualTypeArguments) {
            hashCode ^= actualTypeArgument.hashCode();
        }
        if (ownerType != null) {
            hashCode ^= ownerType.hashCode();
        }
        return hashCode;
    }

    public TypeWrapper getRawType() {
        return rawType;
    }

    @Override
    public Class<?> getRawClass() {
        return rawType.unwrap();
    }

    @Override
    public boolean isAssignableFrom(TypeWrapper wrapper) {
        if (wrapper instanceof ParameterizedTypeWrapper) {
            ParameterizedTypeWrapper parameterizedTypeWrapper = (ParameterizedTypeWrapper) wrapper;
            if (!rawType.isAssignableFrom(parameterizedTypeWrapper.rawType)) {
                return false;
            }
            for (int i = 0; i < actualTypeArguments.length; i++) {
                if (!contains(actualTypeArguments[i], parameterizedTypeWrapper.actualTypeArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        if (wrapper instanceof ClassTypeWrapper) {
            if (!rawType.equals(wrapper)) {
                return false;
            }
            for (TypeWrapper typeArgument : actualTypeArguments) {
                if (!(typeArgument instanceof WildcardWrapper)) {
                    return false;
                }
                WildcardWrapper wildcard = (WildcardWrapper) typeArgument;
                if (wildcard.getLowerBound() != null || !wildcard.getUpperBound().getRawClass().equals(Object.class)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    public static boolean contains(TypeWrapper type1, TypeWrapper type2) {
        if (type1 instanceof WildcardWrapper) {
            WildcardWrapper wildcardType1 = (WildcardWrapper) type1;
            TypeWrapper bound1 = wildcardType1.getLowerBound();
            if (bound1 != null) {
                // type1 = ? super T
                TypeWrapper bound2;
                if (type2 instanceof WildcardWrapper) {
                    bound2 = ((WildcardWrapper) type2).getLowerBound();
                    if (bound2 == null) {
                        // type2 = ? extends S, never contained
                        return false;
                    }
                } else {
                    bound2 = type2;
                }
                return bound2.isAssignableFrom(bound1);
            } else {
                // type 1 = ? extends T
                bound1 = wildcardType1.getUpperBound();
                TypeWrapper bound2;
                if (type2 instanceof WildcardWrapper) {
                    bound2 = ((WildcardWrapper) type2).getUpperBound();
                } else {
                    bound2 = type2;
                }
                return bound1.isAssignableFrom(bound2);
            }
        }

        return type1.equals(type2);
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
        if (o instanceof ParameterizedTypeWrapper) {
            ParameterizedTypeWrapper that = (ParameterizedTypeWrapper) o;
            if (this == that) {
                return true;
            } else {
                return (ownerType == null ? that.ownerType == null : ownerType.equals(that.ownerType))
                        && (rawType == null ? that.rawType == null : rawType.equals(that.rawType))
                        && Arrays.equals(actualTypeArguments, that.actualTypeArguments);
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

    public ParameterizedTypeWrapper substitute(TypeVariable<?> typeVariable, TypeWrapper type) {
        TypeWrapper[] newArguments = new TypeWrapper[actualTypeArguments.length];
        for (int i = 0; i < newArguments.length; i++) {
            TypeWrapper argument = actualTypeArguments[i];
            if (argument instanceof TypeVariableTypeWrapper) {
                TypeVariableTypeWrapper candidate = (TypeVariableTypeWrapper) argument;
                if (candidate.getName().equals(typeVariable.getName())) {
                    newArguments[i] = type;
                    continue;
                }
            }
            newArguments[i] = argument;
        }
        return new ParameterizedTypeWrapper(newArguments, rawType, ownerType);
    }

    ParameterizedTypeWrapper substituteAll(TypeWrapper[] newArguments) {
        if (actualTypeArguments.length != newArguments.length) {
            throw new IllegalArgumentException(
                "Expecting " + actualTypeArguments.length + " type arguments but got " + newArguments.length + ".");
        }
        return new ParameterizedTypeWrapper(newArguments, rawType, ownerType);
    }

    public TypeWrapper[] getActualTypeArguments() {
        return actualTypeArguments;
    }
}
