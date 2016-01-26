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

import java.util.Arrays;

class WildcardTypeWrapper implements WildcardWrapper {
    private final TypeWrapper[] upperBounds;
    private final TypeWrapper[] lowerBounds;
    private final int hashCode;

    public WildcardTypeWrapper(TypeWrapper[] upperBounds, TypeWrapper[] lowerBounds, int hashCode) {
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
        this.hashCode = hashCode;
    }

    @Override
    public Class<?> getRawClass() {
        if (upperBounds.length > 0) {
            return upperBounds[0].getRawClass();
        }
        return Object.class;
    }

    @Override
    public boolean isAssignableFrom(TypeWrapper wrapper) {
        return ParameterizedTypeWrapper.contains(this, wrapper);
    }

    @Override
    public TypeWrapper getUpperBound() {
        return upperBounds[0];
    }

    @Nullable
    @Override
    public TypeWrapper getLowerBound() {
        return lowerBounds.length > 0 ? lowerBounds[0] : null;
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        for (TypeWrapper upperBound : upperBounds) {
            upperBound.collectClasses(builder);
        }
        for (TypeWrapper lowerBound : lowerBounds) {
            lowerBound.collectClasses(builder);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WildcardTypeWrapper)) {
            return false;
        } else {
            WildcardTypeWrapper var2 = (WildcardTypeWrapper) o;
            return Arrays.equals(this.lowerBounds, var2.lowerBounds) && Arrays.equals(this.upperBounds, var2.upperBounds);
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

        TypeWrapper[] bounds = lowerBounds;
        StringBuilder sb = new StringBuilder();

        if (lowerBounds.length > 0) {
            sb.append("? super ");
        } else {
            if (upperBounds.length > 0 && !upperBounds[0].getRawClass().equals(Object.class)) {
                bounds = this.upperBounds;
                sb.append("? extends ");
            } else {
                return "?";
            }
        }

        assert bounds.length > 0;

        boolean first = true;
        for (TypeWrapper bound : bounds) {
            if (!first) {
                sb.append(" & ");
            }

            first = false;
            sb.append(bound.getRepresentation(full));
        }

        return sb.toString();
    }
}
