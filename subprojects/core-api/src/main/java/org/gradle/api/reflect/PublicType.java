/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.reflect;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows to declare the preferred public type of a DSL object.
 *
 * The public type of an object is the one exposed to statically-typed consumers, such as Kotlin build scripts, by default.
 *
 * This annotation can be used in three ways:
 * <ul>
 *     <li>{@code @PublicType} without parameters means that annotated type is the public type</li>
 *     <li>{@code @PublicType(type = SomeOtherType.class)} means that {@code SomeOtherType} is the public type of the annotated type</li>
 *     <li>{@code @PublicType(supplier = ThePublicTypeSupplier.class)} means that {@code @ThePublicTypeSupplier}, which must implement {@link PublicTypeSupplier}, must be used to resolve the public type of the annotated type</li>
 * </ul>
 *
 * Using a {@link PublicTypeSupplier} allows to capture high fidelity generic type tokens with {@link TypeOf}.
 *
 * Note that this annotation should be used on classes only, interfaces should not be annotated with it.
 * The declared public type of a class can be an interface.
 *
 * Providing both {@code type} and {@code supplier} parameters is unsupported and will throw at runtime.
 *
 * @since 8.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Incubating
public @interface PublicType {

    /**
     * Specifies the concrete class of the public type being defined.
     */
    Class<?> type() default UseAnnotatedType.class;

    /**
     * Specifies a supplied for the concrete class of the public type being defined.
     */
    Class<? extends PublicTypeSupplier> supplier() default NoSupplier.class;

    /**
     * Default value for {@link PublicType} annotation {@code type} parameter.
     *
     * Means that the annotated class is the public type.
     *
     * This type should not be used by user code and is intended for internal handling only.
     *
     * @since 8.1
     */
    @Incubating
    final class UseAnnotatedType {

        private UseAnnotatedType() {}
    }

    /**
     * Default value for {@link PublicType} annotation {@code supplier} parameter.
     *
     * Means that there is no supplied provided and the {@code type} parameter should be used instead.
     *
     * This type should not be used by user code and is intended for internal handling only.
     *
     * @since 8.1
     */
    @Incubating
    final class NoSupplier implements PublicTypeSupplier {

        private NoSupplier() {}

        @Override
        public TypeOf<?> getPublicType() {
            throw new UnsupportedOperationException();
        }
    }
}
