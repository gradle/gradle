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
 * <p>Marks a property as specifying a file or directory that a task destroys.
 * The file or directory belongs to another task.</p>
 *
 * <p>This annotation should be attached to the getter method or the field for the property.</p>
 *
 * <p>This will cause the task to have exclusive access to this file or directory while running.  This means
 * that other tasks that either create or consume this file (by specifying the file or directory as an input
 * or output) cannot execute concurrently with a task that destroys this file. This is useful for tasks that
 * clean up after other tasks such as `clean`.</p>
 *
 * @since 4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Destroys {
}
