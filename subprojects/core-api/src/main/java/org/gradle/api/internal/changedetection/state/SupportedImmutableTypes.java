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
package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Named;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SupportedImmutableTypes {
    public final static List<Class<?>> SCALAR_TYPES = ImmutableList.<Class<?>>of(
        // primitives and wrapped types
        Boolean.class,
        Boolean.TYPE,
        Character.class,
        Character.TYPE,
        Byte.class,
        Byte.TYPE,
        Short.class,
        Short.TYPE,
        Integer.class,
        Integer.TYPE,
        Float.class,
        Float.TYPE,
        Long.class,
        Long.TYPE,
        Double.class,
        Double.TYPE,

        // Numbers
        BigInteger.class,
        BigDecimal.class,
        AtomicBoolean.class,
        AtomicInteger.class,
        AtomicLong.class,

        // Types known as immutable
        String.class,
        File.class
    );

    static boolean isScalarType(Class<?> modelType) {
        return SCALAR_TYPES.contains(modelType) || modelType.isEnum();
    }

    static boolean isSupportedInterface(Class<?> clazz) {
        return Named.class.isAssignableFrom(clazz) && clazz.isInterface();
    }

    public static void describeTo(StringBuilder sb) {
        sb.append("   - primitive types (byte, boolean, char, short, int, long, float, double) and their wrapped types (Byte, ...)\n");
        sb.append("   - an enum\n");
        sb.append("   - an instance of String\n");
        sb.append("   - an instance of File\n");
        sb.append("   - an instance of BigInteger, BigDecimal, AtomicInteger, AtomicBoolean or AtomicLong\n");
        sb.append("   - an interface extending Named\n");
        sb.append("   - an array of the above\n");
    }

    public static <T> boolean isSupportedImmutableType(Class<T> clazz) {
        if (clazz.isArray()) {
            return isSupportedImmutableType(clazz.getComponentType());
        }
        if (isScalarType(clazz)) {
            return true;
        }
        if (isSupportedInterface(clazz)) {
            return true;
        }
        return false;
    }

}
