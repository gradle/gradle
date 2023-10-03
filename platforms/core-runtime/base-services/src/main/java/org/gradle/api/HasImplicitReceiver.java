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

package org.gradle.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a SAM interface as a target for lambda expressions / closures
 * where the single parameter is passed as the implicit receiver of the
 * invocation ({@code this} in Kotlin, {@code delegate} in Groovy) as if
 * the lambda expression was an extension method of the parameter type.
 *
 * <pre>
 *     // copySpec(Action&lt;CopySpec&gt;)
 *     copySpec {
 *         from("./sources") // the given CopySpec is the implicit receiver
 *     }
 * </pre>
 *
 * @since 3.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HasImplicitReceiver {
}
