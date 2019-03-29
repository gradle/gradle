/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.tasks.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Attached to a task property to indicate that the property has been replaced by another. Like {@link Internal}, the property is ignored during up-to-date checks.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property field in Groovy. You should also consider adding {@link Deprecated} to any replaced property.</p>
 *
 * <p>This will cause the task <em>not</em> to be considered out-of-date when the property has changed.</p>
 *
 * @since 5.4
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Incubating
public @interface ReplacedBy {
    /**
     * The Java Bean-style name of the replacement property.
     * <p>
     *     If the property has been replaced with a method named {@code getFooBar()}, then this should be {@code fooBar}.
     * </p>
     */
    String value();
}
