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

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Scalars {
    public final static List<Class<?>> TYPES = ImmutableList.<Class<?>>of(
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

    public static boolean isScalarType(Class<?> modelType) {
        return TYPES.contains(modelType) || modelType.isEnum();
    }
}
