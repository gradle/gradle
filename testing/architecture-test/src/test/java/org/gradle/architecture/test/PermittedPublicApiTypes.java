/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.architecture.test;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.reflect.KClass;
import kotlin.reflect.KProperty;
import org.slf4j.Marker;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The curated allow-list of non-Gradle types permitted to appear in the parameter and
 * return types of Gradle's public API methods.
 *
 * <p>This class is the source of truth for the allowed types. It is referenced by
 * ADR-0013 and enforced by {@link PublicApiCorrectnessTest}.
 *
 * <p>Note that primitives and other Gradle public API types are also permitted.
 */
public final class PermittedPublicApiTypes {

    /**
     * JDK packages whose types are all permitted in public API method signatures.
     *
     * <p>Note: {@code java.util.function} is intentionally excluded. Gradle exposes its own
     * functional types such as {@link org.gradle.api.Action}, {@link org.gradle.api.specs.Spec},
     * and {@link org.gradle.api.Transformer} instead. Mixing these custom types with
     * {@code java.util.function} types would make the public API harder to use, especially for
     * plugin authors.
     */
    public static final List<String> PERMITTED_JDK_PACKAGES = Arrays.asList(
        "java.lang",
        "java.util",
        "java.util.concurrent",
        "java.util.regex",
        "java.lang.reflect",
        "java.io",
        "java.nio.file",
        "java.time"
    );

    /**
     * Individually permitted JDK types that do not reside in one of the {@link #PERMITTED_JDK_PACKAGES}.
     */
    public static final List<Class<?>> PERMITTED_JDK_TYPES = Arrays.asList(
        byte[].class,
        URI.class,
        URL.class,
        Duration.class,
        BigDecimal.class,
        Element.class,
        QName.class,
        BiFunction.class
    );

    /**
     * Permitted Kotlin types, allowing the Kotlin DSL to expose idiomatic signatures.
     */
    public static final List<Class<?>> PERMITTED_KOTLIN_TYPES = Arrays.asList(
        Function1.class,
        KClass.class,
        KClass[].class,
        KProperty.class,
        Pair.class,
        Pair[].class,
        Unit.class
    );

    /**
     * Permitted slf4j types.
     */
    public static final List<Class<?>> PERMITTED_SLF4J_TYPES = Arrays.asList(
        Marker.class
    );
}
