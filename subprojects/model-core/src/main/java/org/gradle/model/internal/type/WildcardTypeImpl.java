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

class WildcardTypeImpl implements WildcardType, TypeWrapper {

    private final TypeWrapper[] upperBounds;
    private final TypeWrapper[] lowerBounds;
    private final int hashCode;

    public WildcardTypeImpl(TypeWrapper[] upperBounds, TypeWrapper[] lowerBounds, int hashCode) {
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
    public String getTypeName() {
        Type[] var1 = this.getLowerBounds();
        Type[] var2 = var1;
        StringBuilder var3 = new StringBuilder();
        if (var1.length > 0) {
            var3.append("? super ");
        } else {
            Type[] var4 = this.getUpperBounds();
            if (var4.length <= 0 || var4[0].equals(Object.class)) {
                return "?";
            }

            var2 = var4;
            var3.append("? extends ");
        }

        assert var2.length > 0;

        boolean var9 = true;
        Type[] var5 = var2;
        int var6 = var2.length;

        for (int var7 = 0; var7 < var6; ++var7) {
            Type var8 = var5[var7];
            if (!var9) {
                var3.append(" & ");
            }

            var9 = false;
            var3.append(var8.getTypeName());
        }

        return var3.toString();
    }

    @Override
    public String toString() {
        return getTypeName();
    }
}
