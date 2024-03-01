/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.api.annotations.features.withstaticreference;

/**
 * A Groovy property access interceptor or an instance method interceptor may be marked with
 * this annotation that specifies the class that the Groovy compiler will resolve the extension to.
 * In this case, an additional synthetic interceptor is added for the static method of the class
 * that "matches" the original interceptor's signature as if it was an extension (i.e. receiver is
 * transformed to the first argument, and the callable kind becomes static). For Groovy properties,
 * the name of the callable is also transformed from "foo" to "getFoo".
 * It is possible to specify the custom name for the extension implementation, if needed.
 */
public @interface WithExtensionReferences {
    Class<?> toClass();
    String methodName() default "";
}
