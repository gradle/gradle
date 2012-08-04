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
 * <p>Attached to a task property to indicate that the task should be skipped when the value of the property is an empty
 * {@link org.gradle.api.file.FileCollection} or directory.</p>
 *
 * <p>This annotation can be used with the following annotations:</p>
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
