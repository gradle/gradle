/*
 * Copyright 2016 the original author or authors.
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
 * <p>Marks a property as specifying a JVM classpath for a task.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <p>
 *     For jar files, the normalized path is empty.
 *     The content of the jar file is normalized so that time stamps and order of the zip entries in the jar file do not matter.
 *     This normalization applies to not only files directly on the classpath, but also
 *     to any jar files found inside directories or nested inside other jar files.
 *     If a directory is a classpath entry, then the root directory itself is ignored.
 *     The files in the directory are sorted and the relative path to the root directory is used as normalized path.
 * </p>
 *
 * <p><strong>Note:</strong> to stay compatible with versions prior to Gradle 3.2, classpath
 * properties need to be annotated with {@literal @}{@link InputFiles} as well.</p>
 *
 * @since 3.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Classpath {
}
