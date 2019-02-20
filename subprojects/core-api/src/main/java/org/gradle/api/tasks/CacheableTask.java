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

import org.gradle.api.specs.Spec;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Attached to a task type to indicate that task output caching should be enabled by default for tasks of this type.</p>
 *
 * <p>Only tasks that produce reproducible and relocatable output should be marked with {@code CacheableTask}.</p>
 *
 * <p>Caching for individual task instances can be enabled and disabled via {@link TaskOutputs#cacheIf(String, Spec)} or disabled via {@link TaskOutputs#doNotCacheIf(String, Spec)}, even if the task type is not annotated with {@link CacheableTask}</p>
 *
 * @since 3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CacheableTask {
}
