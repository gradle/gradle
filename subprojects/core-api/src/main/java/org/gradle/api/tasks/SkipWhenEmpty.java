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
 * <p>Attached to a task property to indicate that the task should be skipped when the value of the property is an empty
 * {@link org.gradle.api.file.FileCollection} or directory.</p>
 *
 * <p>If all of the inputs declared with this annotation are empty, the task will be skipped with a "NO-SOURCE" message.</p>
 *
 * <p>Consider using {@link IgnoreEmptyDirectories} as well, if the task only works on files and not on directories.</p>
 *
 * <p>This annotation implies {@link org.gradle.work.Incremental}, and thus inputs annotated with this annotation can also be queried for changes.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <ul><li>{@link org.gradle.api.tasks.InputFiles}</li>
 *
 * <li>{@link org.gradle.api.tasks.InputDirectory}</li> </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface SkipWhenEmpty {
}
