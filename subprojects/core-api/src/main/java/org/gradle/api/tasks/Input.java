/*
 * Copyright 2010 the original author or authors.
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

import java.lang.annotation.*;

/**
 * <p>Attached to a task property to indicate that the property specifies some input value for the task.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <p>This will cause the task to be considered out-of-date when the property has changed.
 * This annotation cannot be used on a {@link java.io.File} object. If you want to refer to the file path,
 * independently of its contents, return a {@link java.lang.String String} instead which returns the absolute
 * path.
 * If, instead, you want to refer to the contents and path of a file or a directory, use
 * {@link org.gradle.api.tasks.InputFile} or {@link org.gradle.api.tasks.InputDirectory} respectively.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Input {
}
