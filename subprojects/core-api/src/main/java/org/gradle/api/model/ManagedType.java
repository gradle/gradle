/*
 * Copyright 2025 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks types that can be instantiated by Gradle via the managed object infrastructure.
 * <p>
 * Types annotated with this annotation will be automatically instantiated by Gradle
 * when constructing managed objects. See the below example:
 *
 * <pre class='autoTested'>
 * interface MyObject {
 *     ConfigurableFileCollection getFiles();
 *     RegularFileProperty getProperty();
 * }
 *
 * def instance = objects.newInstance(MyObject)
 * instance.files.from(file("foo.txt"))
 * instance.property.set(file("bar.txt"))
 * </pre>
 *
 * @since 9.0.0
 */
@Incubating
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedType {
}
