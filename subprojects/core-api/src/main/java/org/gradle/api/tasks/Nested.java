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

package org.gradle.api.tasks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a property as specifying a nested bean, whose properties should be checked for annotations.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <p>Gradle will attempt to instantiate a nested bean on abstract getter methods annotated with this annotation.
 * This creates a <a href="https://docs.gradle.org/current/userguide/custom_gradle_types.html#read_only_managed_nested_properties">read-only managed nested property</a>.</p>
 *
 * <p>The implementation of the nested bean is tracked as an input, too.
 * This allows tracking behavior such as {@link org.gradle.api.Action}s as task inputs.</p>
 *
 * <p>This annotations supports {@link org.gradle.api.provider.Provider} values by treating the result of {@link org.gradle.api.provider.Provider#get()} as a nested bean.</p>
 *
 * <p>This annotation supports {@link Iterable} values by treating each element as a separate nested bean.
 * As a property name, the index of the element in the iterable prefixed by {@code $} is used, e.g. {@code $0}.
 * If the element implements {@link org.gradle.api.Named}, then the property name is composed of {@link org.gradle.api.Named#getName()} and the index, e.g. {@code name$1}.
 * The ordering of the elements in the iterable is crucial for reliable up-to-date checks and caching.</p>
 *
 * <p>This annotation supports ${@link java.util.Map} values by treating each value of the map as a separate nested bean.
 * The keys of the map are used as property names.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Nested {
}
