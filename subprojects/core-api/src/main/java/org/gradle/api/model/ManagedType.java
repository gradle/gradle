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

package org.gradle.api.model;

import org.gradle.api.Incubating;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks types that can be instantiated by Gradle via the managed object infrastructure.
 * <p>
 * Types annotated with this annotation will be automatically instantiated by Gradle
 * when constructing managed objects.
 *
 * To create a managed property, declare an abstract getter method for the given type.
 *
 * For the managed property to be created properly, the object containing it must be
 * instantiated using the {@link org.gradle.api.model.ObjectFactory#newInstance(Class, Object...)}
 * or as a managed {@link org.gradle.api.tasks.Nested} property.
 *
 * The example below creates a {@code ConfigurableFileCollection} and {@code RegularFileProperty}
 * automatically when the {@code MyObject} instance is instantiated by Gradle:
 *
 * <pre class='autoTested'>
 * interface NestedExample {
 *     Property&lt;String&gt; getExampleMessage();
 * }
 *
 * interface MyObject {
 *     ConfigurableFileCollection getFiles();
 *
 *     RegularFileProperty getProperty();
 *
 *     &commat;Nested
 *     NestedExample getNested();
 * }
 *
 * def instance = objects.newInstance(MyObject)
 * instance.files.from(file("foo.txt"))
 * instance.property = file("bar.txt")
 * instance.nested.exampleMessage = "Hello, World!"
 * </pre>
 *
 * @since 9.0.0
 */
@Incubating
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedType {
}
