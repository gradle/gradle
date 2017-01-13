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

import org.gradle.api.Incubating;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a task input property, specifying that the order of the files is relevant to the task's operation.</p>
 *
 * <p>When a task property is file order sensitive, a change in the order of the files makes the task out-of-date,
 * while an order change in a property that is not file order sensitive allows the task to stay up-to-date.</p>
 *
 * @deprecated This annotation will be removed in Gradle 4.0. For classpath properties, use {@link org.gradle.api.tasks.Classpath}.
 *
 * @since 3.1
 */
@Incubating
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Deprecated
public @interface OrderSensitive {
}
