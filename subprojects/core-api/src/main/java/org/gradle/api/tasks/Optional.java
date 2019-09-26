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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a task property as optional. This means that a value does not have to be specified for the property, but any
 * value specified must meet the validation constraints for the property.</p>
 *
 * <p>This annotation should be attached to the getter method in Java or the property in Groovy.
 * Annotations on setters or just the field in Java are ignored.</p>
 *
 * <ul> <li>{@link org.gradle.api.tasks.Input}</li>
 *
 * <li>{@link org.gradle.api.tasks.InputFile}</li>
 *
 * <li>{@link org.gradle.api.tasks.InputDirectory}</li>
 *
 * <li>{@link org.gradle.api.tasks.InputFiles}</li>
 *
 * <li>{@link org.gradle.api.tasks.OutputFile}</li>
 *
 * <li>{@link org.gradle.api.tasks.OutputDirectory}</li> </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Optional {
}
