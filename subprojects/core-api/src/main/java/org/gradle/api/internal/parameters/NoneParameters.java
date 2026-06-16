/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.parameters;

import org.gradle.internal.Cast;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.lang.reflect.Constructor;

/**
 * Common base for the various {@code *Parameters.None} singletons used to indicate that a parameterized API
 * was declared without parameters.
 *
 * <p>
 * Use {@link #singletonOf(Class)} to obtain the canonical instance of a concrete {@code None} type.
 * </p>
 */
@NullMarked
public abstract class NoneParameters implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final ClassValue<NoneParameters> SINGLETONS = new ClassValue<NoneParameters>() {
        @Override
        protected NoneParameters computeValue(Class<?> type) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return (NoneParameters) constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot instantiate singleton of " + type.getName(), e);
            }
        }
    };

    protected NoneParameters() {
    }

    @Override
    public final String toString() {
        Class<?> enclosing = getClass().getEnclosingClass();
        String prefix = enclosing != null ? enclosing.getSimpleName() : getClass().getSimpleName();
        return prefix + ".None";
    }

    public static <T extends NoneParameters> T singletonOf(Class<T> noneType) {
        return Cast.uncheckedNonnullCast(SINGLETONS.get(noneType));
    }

    @SuppressWarnings("unused")
    protected final Object readResolve() {
        return singletonOf(getClass());
    }
}
