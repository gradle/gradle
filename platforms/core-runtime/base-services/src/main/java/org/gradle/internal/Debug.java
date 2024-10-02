/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal;

import com.google.common.base.Supplier;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

public class Debug {
    public static final boolean ENABLED = false;

    private static final ThreadLocal<AtomicInteger> LEVEL = new ThreadLocal<AtomicInteger>() {
        @Override
        protected AtomicInteger initialValue() {
            return new AtomicInteger();
        }
    };

    public static void println(Supplier<?> messageSource) {
        if (!ENABLED) {
            return;
        }
        println(asString(messageSource));
    }

    public static void println(Object messageSource) {
        if (!ENABLED) {
            return;
        }
        String prefix = StringUtils.repeat(" ", LEVEL.get().get() * 2);
        System.out.println(prefix + asString(messageSource));
    }

    private static String asString(Supplier<?> obj) {
        return obj.get().toString();
    }

    private static String asString(Object obj) {
        return obj.toString();
    }

    public static void trace(Supplier<?> obj) {
        if (!ENABLED) {
            return;
        }
        trace(asString(obj));
    }

    public static void trace(Object message) {
        if (!ENABLED) {
            return;
        }
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        println(getPrefix() + asString(message));
        for (StackTraceElement element : trace) {
            if (element.getClassName().contains("org.gradle") && !element.getMethodName().startsWith("invoke")) {
                println("\t*" + element);
            }
        }
    }

    @Nonnull
    public static String getPrefix() {
        return "[" + Thread.currentThread().getName() + "] ";
    }

    @Nullable
    public static <I, O> O goInto(@Nullable I instance, InternalTransformer<O, I> action) {
        if (!ENABLED || instance == null) {
            return null;
        }
        enter();
        try {
            return action.transform(instance);
        } finally {
            leave();
        }
    }

    public static void leave() {
        LEVEL.get().decrementAndGet();
    }

    public static void enter() {
        LEVEL.get().incrementAndGet();
    }
}

