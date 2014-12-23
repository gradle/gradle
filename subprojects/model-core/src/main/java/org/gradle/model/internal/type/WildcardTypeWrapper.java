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

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

class WildcardTypeWrapper implements WildcardType, TypeWrapper {

    private final TypeWrapper[] upperBounds;
    private final TypeWrapper[] lowerBounds;
    private final int hashCode;

    public WildcardTypeWrapper(TypeWrapper[] upperBounds, TypeWrapper[] lowerBounds, int hashCode) {
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
        this.hashCode = hashCode;
    }

    @Override
    public Type[] getUpperBounds() {
        return ModelType.unwrap(upperBounds);
    }

    @Override
    public Type[] getLowerBounds() {
        return ModelType.unwrap(lowerBounds);
    }

    @Override
    public Type unwrap() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WildcardType)) {
            return false;
        } else {
            WildcardType var2 = (WildcardType) o;
            return Arrays.equals(this.getLowerBounds(), var2.getLowerBounds()) && Arrays.equals(this.getUpperBounds(), var2.getUpperBounds());
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        Type[] lowerBounds = getLowerBounds();
        Type[] bounds = lowerBounds;
        StringBuilder sb = new StringBuilder();

        if (lowerBounds.length > 0) {
            sb.append("? super ");
        } else {
            Type[] upperBounds = getUpperBounds();
            if (upperBounds.length > 0 && !upperBounds[0].equals(Object.class)) {
                bounds = upperBounds;
                sb.append("? extends ");
            } else {
                return "?";
            }
        }

        assert bounds.length > 0;

        boolean first = true;
        for (Type bound : bounds) {
            if (!first) {
                sb.append(" & ");
            }

            first = false;
            if (bound instanceof Class) {
                sb.append(((Class) bound).getName());
            } else {
                sb.append(bound.toString());
            }
        }
        return sb.toString();
    }

    @Override
    public String getRepresentation() {
        return toString();
    }
}
