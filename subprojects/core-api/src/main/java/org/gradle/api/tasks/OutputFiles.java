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
 * <p>Marks a property as specifying one or more output files for a task.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <p>This will cause the task to be considered out-of-date when the file paths or contents
 * are different to when the task was last run.</p>
 *
 * <p>When the annotated property is a {@link java.util.Map}, then the keys of the map must be non-empty strings.
 * The values of the map will be evaluated to individual files as per
 * {@link org.gradle.api.Project#file(Object)}.</p>
 *
 * <p>
 * Otherwise the given files will be evaluated as per {@link org.gradle.api.Project#files(Object...)}.
 * Task output caching will be disabled if the outputs contain file trees.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface OutputFiles {
}
