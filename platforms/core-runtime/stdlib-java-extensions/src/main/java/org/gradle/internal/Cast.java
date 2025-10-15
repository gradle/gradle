/*
 * Copyright 2012 the original author or authors.
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

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public abstract class Cast {

    /**
     * Casts the given object to the given type, providing a better error message than the default.
     *
     * The standard {@link Class#cast(Object)} method produces unsatisfactory error messages on some platforms
     * when it fails. All this method does is provide a better, consistent, error message.
     *
     * This should be used whenever there is a chance the cast could fail. If in doubt, use this.
     *
     * @param outputType The type to cast the input to
     * @param object The object to be cast (must not be {@code null})
     * @param <O> The type to be cast to
     * @param <I> The type of the object to be vast
     * @return The input object, cast to the output type
     */
    public static <O, I> O cast(Class<O> outputType, I object) {
        try {
            return outputType.cast(object);
        } catch (ClassCastException e) {
            throw new ClassCastException(String.format(
                    "Failed to cast object %s of type %s to target type %s", object, object.getClass().getName(), outputType.getName()
            ));
        }
    }

    /**
     * Casts the given object to the given type, providing a better error message than the default.
     *
     * The standard {@link Class#cast(Object)} method produces unsatisfactory error messages on some platforms
     * when it fails. All this method does is provide a better, consistent, error message.
     *
     * This should be used whenever there is a chance the cast could fail. If in doubt, use this.
     *
     * @param outputType The type to cast the input to
     * @param object The object to be cast
     * @param <O> The type to be cast to
     * @param <I> The type of the object to be vast
     * @return The input object, cast to the output type
     */
    @Nullable
    public static <O, I> O castNullable(Class<O> outputType, @Nullable I object) {
        if (object == null) {
            return null;
        }
        return cast(outputType, object);
    }

    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    @Contract("!null -> !null")
    public static <T> @Nullable T uncheckedCast(@Nullable Object object) {
        return (T) object;
    }

    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public static <T> T uncheckedNonnullCast(Object object) {
        return (T) object;
    }

    /**
     * Strips nullability from the type. This is useful to work around type system limitations, but must be used carefully.
     * As a rule of thumb, do not use it outside situations where {@code T} is a type parameter that can hold both nullable and non-nullable types.
     * <p>
     * A typically safe pattern is to use it when the generic type parameter {@code T} can be nullable, but has to be mixed with {@code @Nullable T}.
     * For example:
     * <pre>
     *     &lt;T extends{@literal @}Nullable Object&gt; T doFoo(Supplier&lt;T&gt; factory) {
     *        {@literal @}Nullable T value = null;
     *         value = factory.get();
     *
     *         // value holds a valid instance of T (only allowing null if T is a nullable type).
     *         // But NullAway cannot get it and produces an error.
     *         return value;
     *     }
     * </pre>
     * <p>
     * It is not possible to use {@code return Objects.requireNonNull(value)} because {@code value} can legitimately be null for {@code doFoo(() -> null)}.
     * NullAway doesn't allow to use {@link #uncheckedNonnullCast(Object)} either. Using {@code return Cast.unsafeStripNullable(value)} is actually safe.
     * <p>
     * <b>Put a comment explaining the reasoning why this cast is safe nearby.</b>
     *
     * @param object the nullable instance that actually holds a valid value of the type {@code T}
     * @return the given value with explicit nullability annotation stripped.
     * @param <T> the type
     * @see <a href="https://github.com/uber/NullAway/wiki/Suppressing-Warnings#downcasting">NullAway docs on "downcasting"</a>
     */
    @SuppressWarnings("NullAway") // See the javadoc
    public static <T extends @Nullable Object> T unsafeStripNullable(@Nullable T object) {
        return object;
    }
}
