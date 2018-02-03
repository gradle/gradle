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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a property as specifying a Java compile classpath for a task. Attaching this annotation to a property means that changes that do not affect the API of the classes in classpath will be ignored. The following kinds of changes to the classpath will be ignored:</p>
 *
 * <ul>
 *     <li>Changes to the path of jar or top level directories.</li>
 *     <li>Changes to timestamps and the order of entries in Jars.</li>
 *     <li>Changes to resources and Jar manifests, including adding or removing resources.</li>
 *     <li>Changes to private class elements, such as private fields, methods and inner classes.</li>
 *     <li>Changes to code, such as method bodies, static initializers and field initializers (except for constants).</li>
 *     <li>Changes to debug information, for example when a change to a comment affects the line numbers in class debug information.</li>
 *     <li>Changes to directories, including directory entries in Jars.</li>
 * </ul>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <p><strong>Note:</strong> to stay compatible with versions prior to Gradle 3.4, classpath
 * properties need to be annotated with {@literal @}{@link InputFiles} as well.</p>
 *
 * @since 3.4
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface CompileClasspath {
}
